package sproj.tracking;

import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.prediction.KalmanFilterBuilder;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
    private YOLOModelContainer yoloModelContainer;
//    private FFmpegFrameGrabber grabber;

//    private int numb_of_anmls;

    private Rect cropRect;
    private int[] cropDimensions;       // array of four ints, of the form:  [center_x, center_y, width, height]
    private int videoFrameWidth;
    private int videoFrameHeight;

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
        for (int i = 0; i < numb_of_anmls; i++) {
            x = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameWidth));
            y = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameHeight));
            this.animals.add(
                    new AnimalWithFilter(x, y, colors[i], filterBuilder.getNewKalmanFilter(x, y, 0.0, 0.0))
            );
        }
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

        double displThresh = (frameNumber > 10) ? DISPL_THRESH : prox_start_val;   // start out with large proximity threshold to quickly snap to objects

        double dt = 1000.0 / videoFrameRate;

        ArrayList<BoundingBox> assignedBoxes = new ArrayList<>(boundingBoxes.size());
        BoundingBox closestBox;

        for (AnimalWithFilter animal : animals) {

            min_proximity = displThresh;     // start at max allowed value and then favor smaller values
            closestBox = null;

            for (BoundingBox box : boundingBoxes) {

                if (!assignedBoxes.contains(box)) {  // skip already assigned boxes
                    // circleRadius = Math.round(box[2] + box[3] / 2);  // approximate circle from rectangle dimensions

                    current_proximity = Math.pow(Math.abs(animal.x - box.centerX) ^ 2 + Math.abs(animal.y - box.centerY) ^ 2, 0.5);

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
            /*if (boundingBoxes.size() == animals.size() && closestBox != null) {   // This means min_proximity < displacement_thresh?*/
            if (closestBox != null && min_proximity < prox_start_val / 4.0) {   // && RNN probability, etc etc
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

        CanvasFrame originalShower = new CanvasFrame("Original");
        originalShower.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

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
        String testVideo = "/home/ah2166/Videos/tad_test_vids/1_tad_3.MOV"; //"src/main/resources/videos/IMG_4881.MOV";
        int n_objs = 1;
//        String testVideo = "/home/ah2166/Videos/tad_test_vids/2_tad_1.MOV"; //"src/main/resources/videos/IMG_4881.MOV";
//        int n_objs = 2;
        int[] cropDims = new int[]{130,10,670,670};   // {230,10,700,700};//
        SinglePlateTracker tracker = new SinglePlateTracker(n_objs, true,  cropDims, testVideo);
        tracker.trackVideo(testVideo);

        writeAnimalPointsToFile(tracker.animals, "/home/ah2166/Documents/sproj/tracking_data/motionData/testData1.dat", false);
    }
}
