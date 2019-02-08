package sproj.tracking;

import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.assignment.OptimalAssigner;
import sproj.prediction.KalmanFilterBuilder;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.yolo.YOLOModelContainer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static sproj.util.IOUtils.writeAnimalPointsToSeparateFiles;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static sproj.util.IOUtils.writeAnimalsToCSV;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;


/**
 * This class iterates through the input video feed (from a file or a camera device),
 * and implements tracking functions to record the movement data of the subject animals.
 *
 * The recorded data is intermittently passed to the IOUtils class to be written (or appended) to file.
 */
public class SinglePlateTracker extends Tracker {

    private ArrayList<Animal> animals = new ArrayList<>();
    private DetectionsParser detectionsParser = new DetectionsParser();
    private OptimalAssigner optimalAssigner = new OptimalAssigner();
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
    private YOLOModelContainer yoloModelContainer;

    private String dataSaveNamePrefix;
    private Rect cropRect;
    private int[] cropDimensions;       // array of four ints, of the form:  [center_x, center_y, width, height]
    private int videoFrameWidth;
    private int videoFrameHeight;
    private int[] positionBounds;       // x1, x2, y1, y2

    private boolean SAVE_TO_FILE;

    public SinglePlateTracker(final int n_objs, final boolean drawShapes,
                              final int[] crop, String videoPath, String saveDataFilePrefix) throws IOException {

        this.numb_of_anmls = n_objs;
        this.DRAW_ANML_TRACKS = drawShapes;
        this.CANVAS_NAME = "Tadpole SinglePlateTracker";
        //                       x        y        width    height
        this.cropRect = new Rect(crop[0], crop[1], crop[2], crop[3]);
        this.cropDimensions = crop;

        this.videoFrameWidth = crop[2];
        this.videoFrameHeight = crop[3];

        this.positionBounds = new int[]{0, videoFrameWidth, 0, videoFrameHeight};  // x1, x2, y1, y2

        logger.info("initializing tracker...");
        initializeFrameGrabber(videoPath);      // tests if video file is valid and readable first

        createAnimalObjects();

        if (saveDataFilePrefix == null) {
            SAVE_TO_FILE = false;
        } else {
            SAVE_TO_FILE = true;
            this.dataSaveNamePrefix = saveDataFilePrefix;
            //createAnimalFiles(saveDataFilePrefix);
            createAnimalDataCSV(saveDataFilePrefix);
        }
        logger.info("warming up model");
        yoloModelContainer = new YOLOModelContainer();  // loads the model from file in its constructor
    }

    public int getFrameNumb() {
        return grabber.getFrameNumber();
    }

    public int getTotalFrames() {
        return grabber.getLengthInVideoFrames();
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


        /** TODO REMOVE *
        for (Animal anml : animals) {
            prevpoints.put(anml, new int[]{anml.x, anml.y});
        }
        * TODO REMOVE **/

        updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp() / 1000L);

        /** TODO REMOVE

        double displaceThresh = 45.0;
        for (Animal anml : animals) {
            int[] prevPoint = prevpoints.get(anml);
            double disp = Math.pow(
                    Math.pow(anml.x-prevPoint[0], 2) +
                            Math.pow(anml.y-prevPoint[1], 2), 0.5
            );
            if (disp >= displaceThresh) {
                // System.out.println("Identity swap");
                identitySwitches++;
            }
        }

         TODO REMOVE **/


        return frameConverter.convert(frameImg);

    }

    public final List<Animal> getAnimals() {
        return this.animals;
    }


    /**
     * Create new Animal objects and distribute them diagonally across screen, so they attach themselves
     * to the real subject animals more quickly and with fewer conflicts
     */
    @Override
    protected void createAnimalObjects() {

        KalmanFilterBuilder filterBuilder = new KalmanFilterBuilder();

        // BGR not RGB
        int[][] colors = {
                // magenta          cyan           slate blue        green          blue          red
                {255, 0, 255},   {255, 255, 0},  {160,40,40},  {0, 255, 0},   {255, 0, 0}, {0, 0, 255},
                // yellow
                {0, 255, 255}, {47, 107, 85}, {113, 179, 60},  {0, 180, 0},   {160, 160, 0}, {0, 0, 0},
                {0, 255, 127},  {40, 46, 78},  {160, 160, 160}, {90, 90, 90},  {202, 204, 249}
        };

        // distribute
        int x, y;
        int[] clr;
        for (int i = 0; i < numb_of_anmls; i++) {
            x = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameWidth));
            y = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameHeight));
            clr = colors[i];
            this.animals.add(
                    new Animal(x, y, positionBounds, new Scalar(clr[0],clr[1], clr[2], 1.0),  //colors[i],
                            filterBuilder.getNewKalmanFilter(x, y, 0.0, 0.0))
            );
        }
    }

    @Override
    protected void createAnimalFiles(String baseFilePrefix) throws IOException {

        for (Animal a : animals) {
            try (FileWriter writer = new FileWriter(baseFilePrefix + "_anml" + animals.indexOf(a) + ".dat")) {
                writer.write(String.format("Animal Number %d | BGRA color label: %s\n", animals.indexOf(a), a.color.toString()));

            }
        }
    }

    /**
     *
     * Create CSV files with headers: Timestamp, Animal 1, Animal 2, etc
     * instead of numbering animals, which is not useful,
     * the identification label is their color, in BGRA format
     * @param baseFileName
     * @throws IOException
     */
    private void createAnimalDataCSV(String baseFileName) throws IOException {

        // try with resources automatically closes Writer objects
        try (PrintWriter writer = new PrintWriter(new FileWriter(baseFileName + ".csv"))) {

            StringBuilder sb = new StringBuilder();
            sb.append("Timestamp,");

            for (Animal a : animals) {

                int r = (int) Math.round(a.color.red());
                int g = (int) Math.round(a.color.green());
                int b = (int) Math.round(a.color.blue());

                sb.append(String.format("Anml-RGB-(%d-%d-%d)", r,g,b));

                if (animals.indexOf(a) != animals.size()-1) {
                    sb.append(",");
                }
            }
            writer.write(sb.toString());
            writer.println();  // cross-platform compatible
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

        if (frameNumber <= NUMB_FRAMES_FOR_INIT) {

            // the length of the diagonal across the frame--> the largest possible displacement distance for an object in the image
            int prox_start_val = (int) Math.round(Math.sqrt(Math.pow(frameImage.rows(), 2) + Math.pow(frameImage.cols(), 2)));

            for (Animal anml : animals) {
                anml.setCurrCostNonAssignnmnt(prox_start_val);
            }

        }


        // TODO you have to figure out how to prevent the dynamic cost from just making them swap every few frames

        // todo   --> check for whether box has overlap with an animal already and then increase that cost?


        // TODO:   DECREASE Model confidence threshold and INCREASE NMS threshold
        // todo    when lots of tadpoles are close together

        // int proximityCounter


        // todo    figure out a way to prevent swapping when the cost of non assignment gets really high
        // todo    from missing many frames in a row


        // todo calculate the confidence of how true each position update is, and write that to file too


        double dt = 1.0 / videoFrameRate;

        /*optimalAssigner.DEFAULT_COST_OF_NON_ASSIGNMENT = prox_start_val;
            optimalAssigner.ADD_NULL_FOR_EACH_ANIMAL = false;
        } else {
            optimalAssigner.DEFAULT_COST_OF_NON_ASSIGNMENT = Animal.DEFAULT_COST_OF_NON_ASSIGNMENT;
            optimalAssigner.ADD_NULL_FOR_EACH_ANIMAL = true;
        }*/


        // each animal has its own dynamic COST_OF_NON_ASSIGNMENT
        for (BoundingBox box : boundingBoxes) {
            if (DRAW_RECTANGLES) {
                // this rectangle drawing will be removed later
                rectangle(frameImage, new Point(box.topleftX, box.topleftY),
                        new Point(box.botRightX, box.botRightY), Scalar.RED, 1, CV_AA, 0);
            }
        }


        final List<OptimalAssigner.Assignment> assignments = optimalAssigner.getOptimalAssignments(animals, boundingBoxes);

        for (OptimalAssigner.Assignment assignment : assignments) {

            if (assignment.animal == null) {
                continue;
            }

            if (assignment.box == null) {       // no assignment
                assignment.animal.predictTrajectory(dt, timePos);
            } else {
                assignment.animal.updateLocation(
                        assignment.box.centerX, assignment.box.centerY, dt, timePos, false
                );
            }
        }


        if (DRAW_ANML_TRACKS) {
            for (Animal animal : animals) {
                traceAnimalOnFrame(frameImage, animal, 1.0);             // call this here so that this.animals doesn't have to be iterated through again
            }

        }
    }

    private void eqHist(Mat img) {
        equalizeHist(img, img);
    }

    private void clahe(Mat img) {
        GaussianBlur(img, img, new Size(3,3), 0.0);
        CLAHE clahe = createCLAHE(2.0, new Size(3,3));
        clahe.apply(img, img);
    }


    /**
     * OLD CODE  for quick testing purposes only
     *
     * Note that grabber has been initialized by the time this function is called
     *
     * @throws IOException
     * @throws InterruptedException
     */

    public void trackVideo() throws IOException, InterruptedException {

        try {
            track();
        // allow exceptions to be raised
        } finally {
            tearDown();
        }
    }

    private void track() throws InterruptedException, IOException {

        CanvasFrame canvasFrame = new CanvasFrame("Raw Frame");
        canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        CanvasFrame tracking = new CanvasFrame("Tracker Display");
        tracking.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

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
        int saveFrequency = 150;    // save data every 150 frames (5 seconds)
        int totalFrames = grabber.getLengthInVideoFrames();

        boolean exitLoop = false;

        Frame frame;
        while ((frame = grabber.grabImage()) != null && !exitLoop) {

            time1 = System.currentTimeMillis();

//            Mat frameImg = frameConverter.convertToMat(frame);
            Mat frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);   // crop the frame

            Mat trackingOnly = frameImg.clone();


            cvtColor(frameImg, frameImg, COLOR_RGB2GRAY);
//            eqHist(frameImg);
            //clahe(frameImg);
            cvtColor(frameImg, frameImg, COLOR_GRAY2RGB);

            cvtColor(trackingOnly, trackingOnly, COLOR_RGB2GRAY);
//            eqHist(trackingOnly);
            clahe(trackingOnly);
            cvtColor(trackingOnly, trackingOnly, COLOR_GRAY2RGB);
//            resize(trackingOnly, trackingOnly, new Size(IMG_WIDTH, IMG_HEIGHT));



            resize(frameImg, frameImg, new Size(IMG_WIDTH, IMG_HEIGHT));
            detectedObjects = yoloModelContainer.runInference(frameImg);  // frameImg

//            Java2DFrameConverter paintConverter = new Java2DFrameConverter();
//            Component[] arr = canvasFrame.getComponents();
//            canvasFrame.getComponent(0);
//            paintConverter.getBufferedImage(frame);

            boundingBoxes = detectionsParser.parseDetections(detectedObjects);

            /** TODO REMOVE *
            for (Animal anml : animals) {
                prevpoints.put(anml, new int[]{anml.x, anml.y});
            }
            * TODO REMOVE **/


            updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp() / 1000L);

            /** TODO REMOVE *

            double displaceThresh = 60.0;
            for (Animal anml : animals) {
                int[] prevPoint = prevpoints.get(anml);
                double disp = Math.pow(
                        Math.pow(anml.x-prevPoint[0], 2) +
                        Math.pow(anml.y-prevPoint[1], 2), 0.5
                );
                if (disp >= displaceThresh) {
                    System.out.println("Identity swap");
                    identitySwitches++;
                }
            }

            * TODO REMOVE **/


//            System.out.println("Loop time: " + (System.currentTimeMillis() - time1) / 1000.0 + "s");

            keyEvent = canvasFrame.waitKey(msDelay);
            if (keyEvent != null) {

                keyChar = keyEvent.getKeyChar();

                switch(keyChar) {

                    case KeyEvent.VK_ESCAPE: exitLoop = true; break;      // hold escape key or 'q' to quit
                    case KeyEvent.VK_Q: exitLoop = true; break;
                    case KeyEvent.VK_P: Thread.sleep(1000); break;// pause? ;
                }

            }

            if (DRAW_ANML_TRACKS) {
                double scaleMultiplier = trackingOnly.rows() / (double) frameImg.rows();
                for (Animal animal : animals) {
                    traceAnimalOnFrame(trackingOnly, animal, scaleMultiplier);             // call this here so that this.animals doesn't have to be iterated through again
                }
            }

            canvasFrame.showImage(frameConverter.convert(frameImg));
            tracking.showImage(frameConverter.convert(trackingOnly));

            frameNo = grabber.getFrameNumber();

            // todo System.out.print("\r" + (frameNo + 1) + " of " + totalFrames + " frames processed");

            // todo: calculate uncertainty of each point / assignment and write that value to file for each point
            if (SAVE_TO_FILE && frameNo % saveFrequency == 0) {
                //writeAnimalPointsToSeparateFiles(this.animals, dataSaveNamePrefix, true, true);
                writeAnimalsToCSV(this.animals, dataSaveNamePrefix, true);
                for (Animal animal : animals) {
                    animal.clearPoints();
                }
                System.out.println("Saved to CSV file");
            }

        }

        canvasFrame.dispose();
        tracking.dispose();
        grabber.release();
    }


    public static void main(String[] args) throws IOException, InterruptedException {

        String videoPath = "/home/ah2166/Videos/tad_test_vids/trialVids/4tads/";


        String[] testVideos = new String[]{
                "IMG_5193", "IMG_5194", "IMG_5195", "IMG_5196", "IMG_5197", "IMG_5198", "IMG_5199",
                "IMG_5200", "IMG_5201", "IMG_5202", "IMG_5203", "IMG_5204", "IMG_5205",
                "IMG_5206", "IMG_5207", "IMG_5208", "IMG_5209", "IMG_5210", "IMG_5211",
        };

        for (String vid : testVideos) {

            String fullPath = videoPath + vid + ".MOV";
            int n_objs = 4;

            //***** Note that x + width must be <= original image width, and y + height must be <= original image height**//
            int[] cropDims = new int[]{245, 30, 660, 660};//230,10,700,700};//

            String dataSaveName = String.format("/home/ah2166/Documents/sproj/java/Tadpole-Tracker" +
                    "/data/tracking_data/%s_data", vid);


            SinglePlateTracker tracker = new SinglePlateTracker(
                    n_objs, true, cropDims, fullPath, dataSaveName
            );
            tracker.trackVideo();

        }
    }
}

