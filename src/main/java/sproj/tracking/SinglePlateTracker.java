package sproj.tracking;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_core.Point;
import org.bytedeco.javacv.*;

import org.bytedeco.javacv.Frame;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;

import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;

import static org.bytedeco.javacpp.opencv_highgui.destroyAllWindows;
import static org.bytedeco.javacpp.opencv_highgui.waitKey;
import static org.bytedeco.javacpp.opencv_imgproc.*;


/**
 * This class iterates through the input video feed (from a file or a camera device),
 * and implements tracking functions to record the movement data of the subject animals.
 *
 * The recorded data is intermittently passed to the IOUtils class to be written (or appended) to file.
 */
public class SinglePlateTracker extends Tracker {

    private ArrayList<Animal> animals = new ArrayList<>();
    private DetectionsParser detectionsParser = new DetectionsParser();
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
    private YOLOModelContainer yoloModelContainer;
    private FFmpegFrameGrabber grabber;
    private CanvasFrame canvasFrame;        // the main CanvasFrame object to update graphics with.
    // Not to be instantiated by tracker. A CanvasFrame instance will be passed from outside App class.
//    private FFmpegFrameGrabber grabber;

//    private int numb_of_anmls;

    private Rect cropRect;
    private int[] cropDimensions;       // array of four ints, of the form:  [center_x, center_y, width, height]
    private int videoFrameWidth;
    private int videoFrameHeight;


    private List<BoundingBox> boundingBoxes;
    private List<DetectedObject> detectedObjects;



    public SinglePlateTracker(int n_objs, boolean drawShapes, int[] crop, CanvasFrame canvasFrame, String videoPath) throws IOException {

        this.numb_of_anmls = n_objs;
        this.DRAW_SHAPES = drawShapes;
        this.CANVAS_NAME = "Tadpole SinglePlateTracker";
        this.canvasFrame = canvasFrame;   // todo get rid of this

        this.cropDimensions = crop;
        this.videoFrameWidth = cropDimensions[2];
        this.videoFrameHeight = cropDimensions[3];
        this.cropRect = new Rect(cropDimensions[0], cropDimensions[1], cropDimensions[2], cropDimensions[3]);  // use Range instead of Rect?

        initializeFrameGrabber(videoPath);      // test if video file is valid and readable first
        createAnimalObjects();
        yoloModelContainer = new YOLOModelContainer();  // load model
    }




    /**
     * To be called from the JavaFX application, ...
     *
     * @return the current video frame Mat Object with all animal trajectories and shape traces drawn on it
     * @throws IOException FrameGrabber.Exception if the grabber cannot read the next Frame for some reason.
     * Note that if the grabber reaches the end of the video, it will return null, not raise this exception.
     */
    @Override
    public opencv_core.Mat timeStep() throws IOException {

        Frame frame  = grabber.grabImage();
        if (frame == null) {
            return null;
        }

        Mat frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);   // crop the frame    // TODO: 9/10/18  clone this frame, and rescale the shapes on to the cloned image, so you can pass the original resolution image to the display window
        resize(frameImg, frameImg, new Size(IMG_WIDTH, IMG_HEIGHT));

        detectedObjects = yoloModelContainer.detectImg(frameImg);                   // TODO: 9/10/18  pass the numbers of animals, and if the numbers don't match  (or didn't match in the previous frame?), run again with lower confidence?
        boundingBoxes = detectionsParser.parseDetections(detectedObjects);

        updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp());

        return frameImg;

    }

    @Override
    protected void initializeFrameGrabber(String videoPath) throws FrameGrabber.Exception {
        grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();    // open video file
    }



    @Override
    protected void tearDown() {
        try {
            grabber.release();
        } catch (FrameGrabber.Exception ignored) {
        }
    }


    /**
     * Create new Animal objects and distribute them diagonally across screen, so they attach themselves
     * to the real subject animals more quickly and with fewer conflicts
     */
    @Override
    void createAnimalObjects() {

        int[][] colors = {{100, 100, 100}, {90, 90, 90}, {255, 0, 255}, {0, 255, 255}, {0, 0, 255}, {47, 107, 85},
                {113, 179, 60}, {255, 0, 0}, {255, 255, 255}, {0, 180, 0}, {255, 255, 0}, {160, 160, 160},
                {160, 160, 0}, {0, 0, 0}, {202, 204, 249}, {0, 255, 127}, {40, 46, 78}};

        // distribute
        int x, y;
        for (int i = 0; i < numb_of_anmls; i++) {
            x = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameWidth));
            y = (int) ((i + 1) / ((double) numb_of_anmls * videoFrameHeight));
            this.animals.add(new Animal(x, y, colors[i]));
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

        double min_prox, prox;

        // the length of the diagonal across the frame--> the largest possible displacement distance for an object in the image
        int prox_start_val = (int) Math.round(Math.sqrt(Math.pow(frameImage.rows(), 2) + Math.pow(frameImage.cols(), 2)));

        double displThresh = (frameNumber < 10) ? prox_start_val : DISPL_THRESH;   // start out with large proximity threshold to quickly snap to objects

        ArrayList<BoundingBox> assignedBoxes = new ArrayList<>(boundingBoxes.size());
        BoundingBox closestBox;

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
                traceAnimalOnFrame(frameImage, animal);             // call this here so that this.animals doesn't have to be iterated through again
            }
        }
    }
}