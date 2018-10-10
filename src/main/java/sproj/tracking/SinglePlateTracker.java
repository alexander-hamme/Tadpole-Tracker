package sproj.tracking;

import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.assignment.OptimalAssigner;
import sproj.prediction.KalmanFilterBuilder;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static sproj.util.IOUtils.writeAnimalPointsToFile;
import static org.bytedeco.javacpp.opencv_imgproc.*;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;


/**
 * This class iterates through the input video feed (from a file or a camera device),
 * and implements tracking functions to record the movement data of the subject animals.
 *
 * The recorded data is intermittently passed to the IOUtils class to be written (or appended) to file.
 */
public class SinglePlateTracker extends Tracker {

    private ArrayList<AnimalWithFilter> animals = new ArrayList<>();
    private DetectionsParser detectionsParser = new DetectionsParser();
    private OptimalAssigner optimalAssigner = new OptimalAssigner();
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
    private YOLOModelContainer yoloModelContainer;
//    private FFmpegFrameGrabber grabber;

//    private int numb_of_anmls;



    private Rect cropRect;
    private int[] cropDimensions;       // array of four ints, of the form:  [center_x, center_y, width, height]
    private int videoFrameWidth;
    private int videoFrameHeight;
    private int[] positionBounds;

//    private List<BoundingBox> boundingBoxes;
//    private List<DetectedObject> detectedObjects;


    public SinglePlateTracker(int n_objs, boolean drawShapes, int[] crop, String videoPath) throws IOException {

        this.numb_of_anmls = n_objs;
        this.DRAW_SHAPES = drawShapes;
        this.CANVAS_NAME = "Tadpole SinglePlateTracker";

        this.cropDimensions = crop;
        this.videoFrameWidth = cropDimensions[2];
        this.videoFrameHeight = cropDimensions[3];
        this.cropRect = new Rect(cropDimensions[0], cropDimensions[1], cropDimensions[2], cropDimensions[3]);  // use Range instead of Rect?

        this.positionBounds = new int[]{0, videoFrameWidth, 0, videoFrameHeight};

        logger.info("initializing");
        initializeFrameGrabber(videoPath);      // test if video file is valid and readable first
        logger.info("creating animals");
        createAnimalObjects();
        logger.info("warming up");
        yoloModelContainer = new YOLOModelContainer();  // load model
    }


    /**
     * Called from the main JavaFX application with each call to the Animation Timer's update() function
     *
     * @return the current video frame Mat Object with all animal trajectories and shape traces drawn on it
     * @throws IOException FrameGrabber.Exception if the grabber cannot read the next Frame for some reason.
     * Note that if the grabber reaches the end of the video, it will return null, not raise this exception.
     */
    @Override
    public Frame timeStep() throws IOException {

        Frame frame  = grabber.grabImage();
        if (frame == null) {
            return null;
        }

        Mat frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);   // crop the frame    // TODO: 9/10/18  clone this frame, and rescale the shapes on to the cloned image, so you can pass the original resolution image to the display window
        resize(frameImg, frameImg, new Size(IMG_WIDTH, IMG_HEIGHT));

        List<DetectedObject> detectedObjects = yoloModelContainer.runInference(frameImg);                   // TODO: 9/10/18  pass the numbers of animals, and if the numbers don't match  (or didn't match in the previous frame?), run again with lower confidence?
        List<BoundingBox> boundingBoxes = detectionsParser.parseDetections(detectedObjects);

        updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp());

        return frameConverter.convert(frameImg);

    }


    /**
     * Create new Animal objects and distribute them diagonally across screen, so they attach themselves
     * to the real subject animals more quickly and with fewer conflicts
     */
    @Override
    protected void createAnimalObjects() {

        KalmanFilterBuilder filterBuilder = new KalmanFilterBuilder();

        int[][] colors = {{100, 100, 100}, {90, 90, 90}, {255, 0, 255}, {0, 255, 255}, {0, 0, 255}, {47, 107, 85},
                {113, 179, 60}, {255, 0, 0}, {255, 255, 255}, {0, 180, 0}, {255, 255, 0}, {160, 160, 160},
                {160, 160, 0}, {0, 0, 0}, {202, 204, 249}, {0, 255, 127}, {40, 46, 78}};

        // distribute
        int x, y;
        int[] clr;
        for (int i = 0; i < numb_of_anmls; i++) {
            x = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameWidth));
            y = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameHeight));
            clr = colors[i];
            this.animals.add(
                    new AnimalWithFilter(x, y, positionBounds, new Scalar(clr[0],clr[1], clr[2], 1.0),  //colors[i],
                            filterBuilder.getNewKalmanFilter(x, y, 0.0, 0.0))
            );
        }
    }

    private boolean assignmentIsReasonable(AnimalWithFilter anml, BoundingBox box, int frameNumber) {

        if (frameNumber <= NUMB_FRAMES_FOR_INIT) { return true; }

        double displacementThreshMultiplier = 5.0;
        double dt = 1.0 / videoFrameRate;
        // todo use predicted position / velocity values?

        double proximity = Math.pow(Math.pow((anml.x - box.centerX),2) + Math.pow((anml.y - box.centerY), 2), 0.5);



        // is this is across the 416 x 416 image or the full image?
        double minDispl = 5.0;      // use average velocity

        double maxDispl = 25.0;     // todo use actual data for these...  e.g. calculate using mean/the max of displacement values over time

        double reasonableDisplacement = displacementThreshMultiplier * Math.pow(Math.pow(anml.vx * dt, 2) + Math.pow(anml.vy * dt, 2), 0.5);


        reasonableDisplacement = (reasonableDisplacement >= minDispl) ? reasonableDisplacement : minDispl;
        reasonableDisplacement = (reasonableDisplacement <= maxDispl) ? reasonableDisplacement : minDispl;

        System.out.println("reasonable displacement = " + reasonableDisplacement + "For reference, max prox is " +
                (int) Math.round(Math.sqrt(Math.pow(416, 2) + Math.pow(416, 2))));

        // todo also check the animal's heading & the angle to the assignment & if it seems reasonable

        if (proximity >= maxDispl) System.out.println("NOPE: " + proximity);

//        return proximity <= reasonableDisplacement;
        return proximity <= maxDispl;

    }


    /**
     * Runs once with each frame
     * @param boundingBoxes list of BoundingBox objects
     * @param frameImage the current video frame
     * @param frameNumber current frame number
     * @param timePos current time stamp in milliseconds
     */
    private void updateObjectTracking(List<BoundingBox> boundingBoxes, Mat frameImage, int frameNumber, long timePos) {

        double min_proximity, current_proximity;

        // the length of the diagonal across the frame--> the largest possible displacement distance for an object in the image   todo move this elsewhere
        int prox_start_val = (int) Math.round(Math.sqrt(Math.pow(frameImage.rows(), 2) + Math.pow(frameImage.cols(), 2)));

        double displThresh = (frameNumber > NUMB_FRAMES_FOR_INIT) ? DISPL_THRESH : prox_start_val;   // start out with large proximity threshold to quickly snap to objects

        double dt = 1.0 / videoFrameRate;


        // TODO:   instead of looping through either the bounding boxes or Animals, you need to loop through them together & minimize the total
        // todo    cost of assignments.  this may require polynomial time, but for such a small number of objects it shouldn't make much difference.

        /*
        double numberOfComputations = this.animals.size() * boundingBoxes.size();

        HashMap<AnimalWithFilter, BoundingBox> optimalAssignments = new HashMap<>(this.animals.size());



        double[] costMatrix = new double[] {

        }


        double maxCostStartValue = prox_start_val * this.animals.size();
        double minimumCost = maxCostStartValue;
        double currentCost;
        for (int i=0; i<numberOfComputations; i++) {

            // cost is defined as the Euclidean distance between the Animal and the BoundingBox


        }
        */


        final List<OptimalAssigner.Assignment> assignments = optimalAssigner.getOptimalAssignments(animals, boundingBoxes);


        for (OptimalAssigner.Assignment assignment : assignments) {

            if (assignment.box == null) {       // no assignment
                assignment.animal.predictTrajectory(dt, timePos);
            } else {
                assignment.animal.updateLocation(
                        assignment.box.centerX, assignment.box.centerY, dt, timePos
                );
                if (DRAW_RECTANGLES) {
                    // this rectangle drawing will be removed later  (?)
                    rectangle(frameImage, new Point(assignment.box.topleftX, assignment.box.topleftY),
                            new Point(assignment.box.botRightX, assignment.box.botRightY), Scalar.RED, 1, CV_AA, 0);
                }
            }
        }


        for (AnimalWithFilter animal : animals) {

            if (DRAW_SHAPES) {
                traceAnimalOnFrame(frameImage, animal);             // call this here so that this.animals doesn't have to be iterated through again
            }
        }

        if (1==0) {

            // todo loop through BoundingBoxes & assign first, then do a separate loop through animals to check which dont have boxes
            ArrayList<AnimalWithFilter> assignedAnimals = new ArrayList<>(this.animals.size());
            AnimalWithFilter closestAnimal;

            for (BoundingBox box : boundingBoxes) {

//            min_proximity = displThresh;     // start at max allowed value and then favor smaller values
                min_proximity = prox_start_val;     // start at max allowed value and then favor smaller values.
                // Illogical assignments will be discarded by assignmentIsReasonable() function

                closestAnimal = null;

                for (AnimalWithFilter animal : animals) {

                    if (!assignedAnimals.contains(animal)) {  // skip already assigned boxes
                        // circleRadius = Math.round(box[2] + box[3] / 2);  // approximate circle from rectangle dimensions

                        current_proximity = Math.pow(Math.pow(animal.x - box.centerX, 2) + Math.pow(animal.y - box.centerY, 2), 0.5);

                        if (current_proximity < min_proximity) {
                            min_proximity = current_proximity;
                            closestAnimal = animal;
                        }
                    }
                }

                if (closestAnimal != null && assignmentIsReasonable(closestAnimal, box, frameNumber)) {   // && RNN probability, etc etc
                    closestAnimal.updateLocation(box.centerX, box.centerY, dt, timePos);
                    assignedAnimals.add(closestAnimal);
                }
                if (DRAW_RECTANGLES) {
                    // this rectangle drawing will be removed later  (?)
                    rectangle(frameImage, new Point(box.topleftX, box.topleftY),
                            new Point(box.botRightX, box.botRightY), Scalar.RED, 1, CV_AA, 0);
                }
            }


            for (AnimalWithFilter animal : animals) {

                if (!assignedAnimals.contains(animal)) {
                    animal.predictTrajectory(dt, timePos);
                }
                if (DRAW_SHAPES) {
                    traceAnimalOnFrame(frameImage, animal);             // call this here so that this.animals doesn't have to be iterated through again
                }
            }
        }

        /**********************************************************************************************/
        /*
        ArrayList<BoundingBox> assignedBoxes = new ArrayList<>(boundingBoxes.size());
        BoundingBox closestBox;

        for (AnimalWithFilter animal : animals) {

            min_proximity = displThresh;     // start at max allowed value and then favor smaller values
            closestBox = null;

            for (BoundingBox box : boundingBoxes) {

                if (!assignedBoxes.contains(box)) {  // skip already assigned boxes
                    // circleRadius = Math.round(box[2] + box[3] / 2);  // approximate circle from rectangle dimensions

                    current_proximity = Math.pow(Math.pow(animal.x - box.centerX), 2 + Math.pow(animal.y - box.centerY), 2, 0.5);

                    if (current_proximity < min_proximity) {
                        min_proximity = current_proximity;
                        closestBox = box;
                    }
                }

                if (DRAW_RECTANGLES) {
                    // this rectangle drawing will be removed later  (?)
                    rectangle(frameImage, new Point(box.topleftX, box.topleftY),
                            new Point(box.botRightX, box.botRightY), Scalar.RED, 1, CV_AA, 0);
                }
            }
            //if (boundingBoxes.size() == animals.size() && closestBox != null) {   // This means min_proximity < displacement_thresh?
            if (closestBox != null && assignmentIsReasonable(animal, closestBox, frameNumber)) {   // && RNN probability, etc etc
                animal.updateLocation(closestBox.centerX, closestBox.centerY, dt, timePos);
                assignedBoxes.add(closestBox);
            } else {
                // todo: instead of min_proximity --> use (Decision tree? / Markov? / SVM? / ???) to determine if the next point is reasonable
                animal.predictTrajectory(dt, timePos);
            }

            if (DRAW_SHAPES) {
                traceAnimalOnFrame(frameImage, animal);             // call this here so that this.animals doesn't have to be iterated through again
            }
        }
        */
    }















    /**
     * OLD CODE    for quick testing purposes only
     *
     * @param videoPath
     * @throws IOException
     * @throws InterruptedException
     */



    public void trackVideo(String videoPath) throws IOException, InterruptedException {

        grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();    // open video file

        try {
            track(cropRect);
        } finally {
            tearDown();
        }
    }

    private void track(Rect cropRect) throws InterruptedException, IOException {

        CanvasFrame canvasFrame = new CanvasFrame("Tracker");
        canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

//        CanvasFrame originalShower = new CanvasFrame("Original");
//        originalShower.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);



        int msDelay = 10;
        List<BoundingBox> boundingBoxes;
        List<DetectedObject> detectedObjects;

        KeyEvent keyEvent;
        char keyChar;

        /** TEMPORARY HACK JUST TO SHOW THE FRAMES*/

        /*canvasFrame = new CanvasFrame("SinglePlateTracker");
        canvasFrame.setLocationRelativeTo(null);     // centers the window
        canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
        canvasFrame.setResizable(true);
        canvasFrame.setVisible(true);*/

        long time1;
        int frameNo;
        int totalFrames = grabber.getLengthInVideoFrames();

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {

            time1 = System.currentTimeMillis();

//            Mat frameImg = frameConverter.convertToMat(frame);
            Mat frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);   // crop the frame

            // clone this, so you can show the original scaled up image in the display window???
            resize(frameImg, frameImg, new Size(IMG_WIDTH, IMG_HEIGHT));



            Mat original = frameImg.clone();



            detectedObjects = yoloModelContainer.runInference(frameImg);    // TODO   pass the numbers of animals, and if the numbers don't match  (or didn't match in the previous frame?), try with lower confidence?

//            Java2DFrameConverter paintConverter = new Java2DFrameConverter();
//            Component[] arr = canvasFrame.getComponents();
//            canvasFrame.getComponent(0);
//            paintConverter.getBufferedImage(frame);

            boundingBoxes = detectionsParser.parseDetections(detectedObjects);

            updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp());


//            System.out.println("Loop time: " + (System.currentTimeMillis() - time1) / 1000.0 + "s");


            keyEvent = canvasFrame.waitKey(msDelay);
            if (keyEvent != null) {

                keyChar = keyEvent.getKeyChar();

                switch(keyChar) {

                    case KeyEvent.VK_ESCAPE: break;      // hold escape key or 'q' to quit
                    case KeyEvent.VK_Q: break;

                    case KeyEvent.VK_P: ;// pause? ;
                }

            }

            canvasFrame.showImage(frameConverter.convert(frameImg));
//            originalShower.showImage(frameConverter.convert(original));

            frameNo = grabber.getFrameNumber();

            // todo System.out.print("\r" + (frameNo + 1) + " of " + totalFrames + " frames processed");

        }

        grabber.release();
    }


    public static void main(String[] args) throws IOException, InterruptedException {
//        String testVideo = "/home/ah2166/Videos/tad_test_vids/1_tad_3.MOV"; //"src/main/resources/videos/IMG_4881.MOV";
//        int n_objs = 1;
//        String testVideo = "/home/ah2166/Videos/tad_test_vids/2_tad_1.MOV"; //"src/main/resources/videos/IMG_4881.MOV";
//        int n_objs = 2;
//        String testVideo = "/home/ah2166/Videos/tad_test_vids/3_tad_1.MOV"; //"src/main/resources/videos/IMG_4881.MOV";
//        int n_objs = 3;
//        int[] cropDims = new int[]{130,10,670,670};   // {230,10,700,700};//

//        String testVideo = "/home/ah2166/Videos/tad_test_vids/trialVids/1_tadpole/IMG_4972.MOV";
//        int n_objs = 1;

//        String testVideo = "/home/ah2166/Videos/tad_test_vids/trialVids/2_tadpoles/IMG_4994.MOV";
//        int n_objs = 2;

//        String testVideo = "/home/ah2166/Videos/tad_test_vids/trialVids/4_tadpoles/IMG_5014.MOV";
//        int n_objs = 4;


        String testVideo = "/home/ah2166/Videos/tad_test_vids/trialVids/8_tadpoles/IMG_5054.MOV";
        int n_objs = 8;


        //***** Note that x + width must be <= original image width, and y + height must be <= original image height**//
        int[] cropDims = new int[]{235,0,720,720};   // {230,10,700,700};//
        SinglePlateTracker tracker = new SinglePlateTracker(n_objs, true,  cropDims, testVideo);
        tracker.trackVideo(testVideo);

        writeAnimalPointsToFile(tracker.animals, "/home/ah2166/Documents/sproj/tracking_data/motionData/testData1.dat", false);
    }
}
