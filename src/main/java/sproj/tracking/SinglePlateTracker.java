package sproj.tracking;

import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_core.Point;
import org.bytedeco.javacv.*;

import org.bytedeco.javacv.Frame;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;

import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;

import static org.bytedeco.javacpp.opencv_highgui.destroyAllWindows;
import static org.bytedeco.javacpp.opencv_highgui.waitKey;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.opencv.imgproc.Imgproc.LINE_AA;


/**
 * This class iterates through the input video feed (from a file or a camera device),
 * and implements tracking functions to record the movement data of the subject animals.
 *
 * The recorded data is intermittently passed to the IOUtils class to be written (or appended) to file.
 */
public class SinglePlateTracker extends Tracker {

    private ArrayList<Animal> animals = new ArrayList<>();
    private DetectionsParser detectionsParser = new DetectionsParser();
    private YOLOModelContainer yoloModelContainer = new YOLOModelContainer();
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
//    private FFmpegFrameGrabber grabber;

//    private int numb_of_anmls;

    private Rect cropRect;
    private int[] cropDimensions;       // array of four ints, of the form:  [center_x, center_y, width, height]
    private int videoFrameWidth;
    private int videoFrameHeight;


    public SinglePlateTracker(int n_objs, boolean drawShapes, int[] crop, CanvasFrame canvasFrame) throws IOException {

        this.numb_of_anmls = n_objs;
        this.DRAW_SHAPES = drawShapes;
        this.CANVAS_NAME = "Tadpole SinglePlateTracker";
        this.canvasFrame = canvasFrame;

        this.cropDimensions = crop;
        this.videoFrameWidth = cropDimensions[2];
        this.videoFrameHeight = cropDimensions[3];
        this.cropRect = new Rect(cropDimensions[0], cropDimensions[1], cropDimensions[2], cropDimensions[3]);  // use Range instead of Rect?

        createAnimalObjects();
    }

    @Override
    void createAnimalObjects() {

        int[][] colors = {{100, 100, 100}, {90, 90, 90}, {255, 0, 255}, {0, 255, 255}, {0, 0, 255}, {47, 107, 85},
                {113, 179, 60}, {255, 0, 0}, {255, 255, 255}, {0, 180, 0}, {255, 255, 0}, {160, 160, 160},
                {160, 160, 0}, {0, 0, 0}, {202, 204, 249}, {0, 255, 127}, {40, 46, 78}};

        // distribute animal objects diagonally across screen, so they attach themselves to the real animals quickly and with fewer conflicts
        int x, y;
        for (int i = 0; i < numb_of_anmls; i++) {
            x = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameWidth));
            y = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameHeight));
            this.animals.add(new Animal(x, y, colors[i]));
        }
    }

    /**
     * The public tracking function which calls internal track() method for actual detection and object tracking.
     * @param videoPath String path to file
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public void trackVideo(String videoPath) throws IOException, InterruptedException {

        grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();    // open video file

        INPUT_FRAME_WIDTH = grabber.getImageWidth();        // todo     use these for canvas frame
        INPUT_FRAME_HEIGHT = grabber.getImageHeight();

        try {
            track(cropRect);
        } finally {
            tearDown();
        }
    }

    private void track(Rect cropRect) throws InterruptedException, IOException {

        int msDelay = 10;
        List<BoundingBox> boundingBoxes;
        List<DetectedObject> detectedObjects;


        int ESCAPE = KeyEvent.VK_ESCAPE;
        int PAUSE = KeyEvent.VK_P;
        KeyEvent keyEvent;
        char keyChar;

        /** TEMPORARY HACK JUST TO SHOW THE FRAMES*/

        canvasFrame = new CanvasFrame("SinglePlateTracker");
        canvasFrame.setLocationRelativeTo(null);     // centers the window
        canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
        canvasFrame.setResizable(true);
        canvasFrame.setVisible(true);

        long time1;

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {

            time1 = System.currentTimeMillis();

//            Mat frameImg = frameConverter.convertToMat(frame);
            Mat frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);   // crop the frame

            // clone this, so you can show the original scaled up image in the display window???
            resize(frameImg, frameImg, new Size(IMG_WIDTH, IMG_HEIGHT));

            detectedObjects = yoloModelContainer.detectImg(frameImg);    // TODO   pass the numbers of animals, and if the numbers don't match  (or didn't match in the previous frame?), try with lower confidence?

//            Java2DFrameConverter paintConverter = new Java2DFrameConverter();
//            Component[] arr = canvasFrame.getComponents();
//            canvasFrame.getComponent(0);
//            paintConverter.getBufferedImage(frame);

            boundingBoxes = detectionsParser.parseDetections(detectedObjects);

            updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp());


            System.out.println("Loop time: " + (System.currentTimeMillis() - time1) / 1000.0 + "s");


            keyEvent = canvasFrame.waitKey(msDelay);
            if (keyEvent != null) {
                keyChar = keyEvent.getKeyChar();
                if (keyChar == ESCAPE) {   // hold escape key to quit
                    break;
                } else if (keyChar == PAUSE) {

                    // todo stop the video stream, but not the whole app?

                    /*Thread.sleep(1000);
                    int pauseDelay = 100;
                    do {
                        Thread.sleep(pauseDelay);
                        keyEvent = canvasFrame.waitKey();
                    } while (keyEvent == null || keyEvent.getKeyChar() != PAUSE);
                    */
                }
            }





            canvasFrame.showImage(frameConverter.convert(frameImg));




            /*logSimpleMessage(

            String.format("%n---------------Time Profiles (s)-------------" +
                            "%nFrame to Mat Conversion:\t%.7f %nResize Mat Object:\t\t\t%.7f %nYolo Detection:\t\t\t\t%.7f" +
                            "%nParse Detections:\t\t\t%.7f %nUpdate Obj Tracking:\t\t%.7f %nDraw Graphics:\t\t\t\t%.7f%n" +
                            "----------------------------------------------%n",
                    (time2 - time1) / 1.0e9, (time3 - time2) / 1.0e9, (time4 - time3) / 1.0e9,
                    (time5 - time4) / 1.0e9, (time6 - time5) / 1.0e9, (time7 - time6) / 1.0e9
            )
            );*/

//            Thread.sleep(10L);


        }
        grabber.release();
        destroyAllWindows();
    }

    /**
     * Runs once on every frame
     * @param boundingBoxes list of BoundingBox objects
     * @param frameImage the current video frame
     * @param frameNumber current frame number
     * @param timePos current time stamp in milliseconds
     */
    private void updateObjectTracking(List<BoundingBox> boundingBoxes, Mat frameImage, int frameNumber, long timePos) {

        double min_prox, prox;

        // the length of the diagonal across the frame--> the largest possible displacement distance for an object in the image
        int prox_start_val = (int) Math.round(Math.sqrt(Math.pow(frameImage.rows(), 2) + Math.pow(frameImage.cols(), 2)));

        double displThresh = (frameNumber < 10) ? prox_start_val : DISPL_THRESH;   // start out with large proximity threshold to quickly snap to objects

        ArrayList<BoundingBox> assignedBoxes = new ArrayList<>(boundingBoxes.size());
        BoundingBox closestBox;


        // TODO: 8/13/18 opencv_highgui.startWindowThread() ???    what is this used for

        for (Animal animal : animals) {

            min_prox = displThresh;     // start at max allowed value and then favor smaller values
            closestBox = null;

            for (BoundingBox box : boundingBoxes) {

                if (!assignedBoxes.contains(box)) {  // skip already assigned boxes
                    // circleRadius = Math.round(box[2] + box[3] / 2);  // approximate circle from rectangle dimensions

                    prox = Math.pow(Math.abs(animal.x - box.centerX) ^ 2 + Math.abs(animal.y - box.centerY) ^ 2, 0.5);

                    if (prox < min_prox) {
                        min_prox = prox;
                        closestBox = box;
                    }
                }

                if (DRAW_RECTANGLES) {
                    // this rectangle drawing will be removed later  (?)
                    rectangle(frameImage, new Point(box.topleftX, box.topleftY),
                            new Point(box.botRightX, box.botRightY), Scalar.RED, 1, CV_AA, 0);
                }
            }
            if (boundingBoxes.size() == animals.size() && closestBox != null) {   // This means min_prox < displacement_thresh?
                // todo: instead of min_prox --> use (Decision tree? / Markov? / SVM? / ???) to determine if the next point is reasonable
                animal.updateLocation(closestBox.centerX, closestBox.centerY, timePos);
                assignedBoxes.add(closestBox);

            } else if (closestBox != null) {
//                System.out.println("First if-statement");

                animal.updateLocation(closestBox.centerX, closestBox.centerY, timePos);
                assignedBoxes.add(closestBox);

            } else {
                System.out.println("Predicting trajectory goes here?");
                animal.updateLocation(animal.x, animal.y, timePos);
            }

            if (DRAW_SHAPES) {
                drawShapesOnImageFrame(frameImage, animal);             // call this here so that this.animals doesn't have to be iterated through again
            }
        }
    }

    /**
     * Note that these drawing functions change the Mat object by changing color values to draw the shapes.
     * @param videoFrameMat
     * @param animal
     */
    private void drawShapesOnImageFrame(Mat videoFrameMat, Animal animal) {
        // info : http://bytedeco.org/javacpp-presets/opencv/apidocs/org/bytedeco/javacpp/opencv_imgproc.html#method.detail

        Scalar circleColor = animal.color; //new Scalar(0,255,0,1);
        circle(videoFrameMat, new Point(animal.x, animal.y), animal.CIRCLE_RADIUS, circleColor);

        // draw trailing trajectory line behind current animal
        int lineThickness = animal.LINE_THICKNESS;
        Iterator<int[]> linePointsIterator = animal.getLinePointsIterator();

        if (linePointsIterator.hasNext()) {

            int[] pt1 = linePointsIterator.next();
            int[] pt2;

            while (linePointsIterator.hasNext()) {

                pt2 = linePointsIterator.next();
                // lineThickness = Math.round(Math.sqrt(animal.LINE_THICKNESS / (animal.linePointsSize - i)) * 2);

                line(videoFrameMat,
                        new Point(pt1[0], pt1[1]),
                        new Point(pt2[0], pt2[1]),
                        animal.color, lineThickness, LINE_AA, 0); // lineThickness, line type, shift
                pt1 = pt2;                                           // -->  line type is LINE_4, LINE_8, or LINE_AA
            }
        } else {
            logger.warn("Line points iterator is empty, failed to draw trajectory paths.");
        }
    }
}