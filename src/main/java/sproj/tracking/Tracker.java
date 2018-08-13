package sproj.tracking;

import static org.bytedeco.javacpp.opencv_highgui.destroyAllWindows;
import static org.bytedeco.javacpp.opencv_highgui.imshow;
import static org.bytedeco.javacpp.opencv_highgui.waitKey;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.opencv.imgproc.Imgproc.LINE_4;

import com.sun.xml.internal.bind.v2.TODO;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;

import org.deeplearning4j.nn.layers.objdetect.DetectedObject;

import org.opencv.imgproc.Imgproc;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.yolo_porting_attempts.YOLOModelContainer;
import static sproj.util.IOUtils.logSimpleMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


/**
 * This class will implement tracking functions and store the recorded data.
 *
 * It should call a function in IOUtils to actually write the data?
 */
public class Tracker {

    static int VERBOSITY_LEVEL = 3;             // 1, 2, 3, or 4
    final double DISPL_THRESH_FRACT = 1.5;      // used for distance thresholding
    final int DISPL_THRESH = 80;
    final int ARRAY_MAX_SIZE = 60;              // buffer size of array to accumulate data
    final int frame_resize_width = 720;
    boolean DRAW_CIRCLES = true;
    int circleRadius = 5;

    private final int IMG_WIDTH = YOLOModelContainer.IMG_WIDTH;
    private final int IMG_HEIGHT = YOLOModelContainer.IMG_HEIGHT;
    private int INPUT_FRAME_WIDTH;
    private int INPUT_FRAME_HEIGHT;


    private YOLOModelContainer yoloModelContainer;
    private ArrayList<Animal> animals = new ArrayList<>();

    public FFmpegFrameGrabber grabber;
    private DetectionsParser detectionsParser = new DetectionsParser();
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

    private int number_of_objs;


    public Tracker(int n_objs, boolean display) throws IOException {
        this.number_of_objs = n_objs;
        yoloModelContainer = new YOLOModelContainer();

    }

    private void setup(int width, int height) {
        int[][] colors = {{100, 100, 100}, {90, 90, 90}, {255, 0, 255}, {0, 255, 255}, {0, 0, 255}, {47, 107, 85},
                {113, 179, 60}, {255, 0, 0}, {255, 255, 255}, {0, 180, 0}, {255, 255, 0}, {160, 160, 160},
                {160, 160, 0}, {0, 0, 0}, {202, 204, 249}, {0, 255, 127}, {40, 46, 78}};

        int x, y;

        for (int i = 0; i < number_of_objs; i++) {
            x = (int) ((i + 1) / ((double) number_of_objs * width));            // distribute animal objects diagonally across screen
            y = (int) ((i + 1) / ((double) number_of_objs * height));
            this.animals.add(new Animal(x, y, colors[i]));
        }
    }


    public void trackVideo(String videoPath, int[] cropDimensions, CanvasFrame canvasFrame) throws InterruptedException, IOException {

        int msDelay = 10;

        // todo should this be in the constructor, or in a different class?
        setup(cropDimensions[2], cropDimensions[3]);

        List<BoundingBox> boundingBoxes;
        List<DetectedObject> detectedObjects;

        Rect cropRect = new Rect(new Point(cropDimensions[0], cropDimensions[1]), new Size(cropDimensions[2], cropDimensions[3]));
        //  new Range(300,600), new Range(200,400)); //


        grabber = new FFmpegFrameGrabber(videoPath);                       // OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(fileName);
        grabber.start();    // open video file

        INPUT_FRAME_WIDTH = grabber.getImageWidth();        // todo are these necessary?
        INPUT_FRAME_HEIGHT = grabber.getImageHeight();


        double time1, time2, time3, time4, time5, time6, time7;

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {

//            time1 = System.nanoTime();

//            Mat frameImg = frameConverter.convertToMat(frame);
            Mat frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);

//            time2 = System.nanoTime();


            // TODO clone this, so you can show the original scaled up image in the display window???
            resize(frameImg, frameImg, new Size(IMG_WIDTH, IMG_HEIGHT));

//            time3 = System.nanoTime();


            detectedObjects = yoloModelContainer.detectImg(frameImg);
            // TODO   pass the numbers of animals, and if the numbers don't match  (or didn't match in the previous frame?), try with lower confidence?
//            time4 = System.nanoTime();

            boundingBoxes = detectionsParser.parseDetections(detectedObjects);

//            time5 = (double) System.nanoTime();


            updateObjectTracking(boundingBoxes, frameImg, grabber.getFrameNumber(), grabber.getTimestamp());

//            time6 = System.nanoTime();


            for (BoundingBox box : boundingBoxes) {
                rectangle(frameImg, new Point(box.topleftX, box.topleftY),
                        new Point(box.botRightX, box.botRightY), Scalar.RED, 1, CV_AA, 0);
            }

//            time7 = System.nanoTime();

            canvasFrame.showImage(frameConverter.convert(frameImg));


/*            logSimpleMessage(

                    String.format("%n---------------Time Profiles (s)-------------" +
                                    "%nFrame to Mat Conversion:\t%.7f %nResize Mat Object:\t\t\t%.7f %nYolo Detection:\t\t\t\t%.7f" +
                                    "%nParse Detections:\t\t\t%.7f %nUpdate Obj Tracking:\t\t%.7f %nDraw Graphics:\t\t\t\t%.7f%n" +
                                    "----------------------------------------------%n",
                            (time2 - time1) / 1.0e9, (time3 - time2) / 1.0e9, (time4 - time3) / 1.0e9,
                            (time5 - time4) / 1.0e9, (time6 - time5) / 1.0e9, (time7 - time6) / 1.0e9
                    )
            );*/

            /**
             char key = (char) waitKey(msDelay);
             if (key == 27) { // Escape key to exit      todo check char number for 'q' and other letters
             destroyAllWindows();
             break;
             }
             */
//            Thread.sleep(10L);
        }
        grabber.release();
        destroyAllWindows();
    }


    private void updateObjectTracking(List<BoundingBox> boundingBoxes, Mat frameImage, int frameNumber, long timePos) {

        double min_prox, prox;
        int prox_start_val = (int) (Math.sqrt(Math.pow(frameImage.rows(), 2) + Math.pow(frameImage.cols(), 2)) + 0.5);
        // start out with large proximity threshold to quickly snap to objects
        double displThresh = (frameNumber > 10) ? DISPL_THRESH : prox_start_val;

        ArrayList<BoundingBox> assignedBoxes = new ArrayList<>(boundingBoxes.size());   //    initialize this outside the main loop?
        BoundingBox closestBox;

        for (Animal animal : animals) {

            min_prox = displThresh;
            closestBox = null;

            for (BoundingBox box : boundingBoxes) {

                if (!assignedBoxes.contains(box)) {  // skip already assigned boxes
//                    circleRadius = Math.round(box[2] + box[3] / 2);  // approximate circle from rectangle dimensions

                    prox = Math.pow(Math.abs(animal.x - box.centerX) ^ 2 + Math.abs(animal.y - box.centerY) ^ 2, 0.5);

                    if (prox < min_prox) {
                        min_prox = prox;
                        closestBox = box;
                    }
                }
            }

            if (boundingBoxes.size() == animals.size() && closestBox != null) {   // This means min_prox < displacement_thresh?
                // todo: instead of min_prox --> use (Decision tree? / Markov? / SVM? / ???) to determine if the next point is reasonable
                animal.updateLocation(closestBox.centerX, closestBox.centerY, timePos);
                assignedBoxes.add(closestBox);

            } else if (closestBox != null) {
                System.out.println("First if-statement");
                animal.updateLocation(closestBox.centerX, closestBox.centerY, timePos);
                assignedBoxes.add(closestBox);

            } else {
                System.out.println("Predicting trajectory goes here?");
                animal.updateLocation(animal.x, animal.y, timePos);
            }

            if (DRAW_CIRCLES) {
                drawShapesOnImageFrame(frameImage, animal);             // call this here so that this.animals doesn't have to be iterated through again
            }
        }
    }


    private void drawShapesOnImageFrame(Mat videoFrameMat, Animal animal) {

        // TODO: 8/12/18    need to rework this to use    org.bytedeco.javacpp.opencv_highgui

        // info : http://bytedeco.org/javacpp-presets/opencv/apidocs/org/bytedeco/javacpp/opencv_imgproc.html#method.detail

        circle(videoFrameMat, new Point(animal.x, animal.y), animal.CIRCLE_RADIUS, new Scalar(0, 255, 0, 1));

        int thickness;

        int[][] linePointsArr = animal.getLinePointsAsArray();

        for (int i = 1; i < linePointsArr.length; i++) {

            if (linePointsArr[i-1] == null || linePointsArr[i] == null) { break; }   // check for null values

            // todo just use one thickness value??
//            thickness = (int) Math.round(Math.sqrt(animal.LINE_THICKNESS / (linePointsArr.length - i)) * 2);  // todo what does this do?
            thickness = (int) animal.LINE_THICKNESS;

            line(videoFrameMat,
                    new Point(linePointsArr[i-1][0], linePointsArr[i-1][1]),
                    new Point(linePointsArr[i][0], linePointsArr[i][1]),
                    animal.color, thickness, LINE_4, 0); // thickness, line type, shift     //LINE_4, LINE_8, or LINE_AA
        }
    }

    public void run(int n_objs, String filename, boolean display) throws FrameGrabber.Exception {

//        this.setup(crop[1] - crop[0], crop[3] - crop[2], n_objs);
        int[] crop = new int[0];

        String videoPath = "";

        int msDelay = 20;

        int frameNumber = 0;
        long current_time_pos = 0L;
        double CONF_THRESH = 0.5;
        double CONF_THRESH_BACKUP = 0.2;  // Backup
        int displThresh = this.DISPL_THRESH;

        // length of diagonal across frame
        int prox_start_val = (int) (Math.sqrt(Math.pow(crop[1] - crop[0], 2) + Math.pow(crop[3] - crop[2], 2)) + 0.5);
        double min_prox, prox;
        boolean numbs_match;

        Double label;               // = 0.;
        Double conf;                // = 0.0;
        Integer[] bbox;             // = new Integer[]{1,2,3,4};
        Object[][] predictions;     // = new Object[][]{new Object[]{label, conf, bbox}};

        int circleRadius;
        int lineThicknessBuffer = 25;
        int[][] linePointsArr;
        int center_x, center_y, wdth, hght;
        Integer[] closestBox;
        ArrayList<Integer[]> boundingBoxes;
        ArrayList<Integer[]> assignedBoxes;

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();

        // todo this is not currently being used...
//        CanvasFrame canvasFrame = new CanvasFrame("Test");
//        canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();

        Frame frame;
        Mat videoFrameMat;

        Rect[] drawingBoxes = new Rect[n_objs];  //todo

        while ((frame = grabber.grabImage()) != null) {  // put in something to write remaining data to file upon reaching last frame

            frameNumber = grabber.getFrameNumber();

            videoFrameMat = converterToMat.convertToMat(frame);

            //todo Mat frame.image  =  crop(....)

            current_time_pos = grabber.getTimestamp();

            //todo  use FrameConverter to convert to the type of array Darknet reads?
            predictions = new Object[0][]; //ycv.detect(net, meta, frame, CONF_THRESH2, CONF_THRESH2)  # thresh=.5, hier_thresh=.5, nms=.45

            // if (predictions.length == animals.size()) { }

            boundingBoxes = new ArrayList<>(predictions.length);
            assignedBoxes = new ArrayList<>(predictions.length);  // array of (x,y) points

            for (Object[] prediction : predictions) {
                label = (Double) prediction[0];
                conf = (Double) prediction[1];
                bbox = (Integer[]) prediction[2];

                boundingBoxes.add(bbox);

                center_x = bbox[0];
                center_y = bbox[1];
                wdth = bbox[2];
                hght = bbox[3];

//                canvasFrame.createGraphics();
                Rect rect = new Rect(center_x - wdth / 2, center_y - hght / 2, wdth, hght);
                rectangle(videoFrameMat, rect, new Scalar(0, 255, 0, 1));


                String box_text = "Prediction = " + Arrays.toString(prediction);
                // And now put it into the image:
                putText(videoFrameMat, box_text, new Point(center_x, center_y),
                        opencv_core.FONT_HERSHEY_PLAIN, 1.0, new Scalar(0, 255, 0, 2.0));
            }

            numbs_match = (boundingBoxes.size() == animals.size());

            // start out with large proximity threshold to quickly snap to objects
            displThresh = (frameNumber > 10) ? DISPL_THRESH : prox_start_val;

            for (Animal animal : animals) {

                min_prox = displThresh;
                closestBox = null;
                circleRadius = 5;     // todo   define this above  or something

                for (Integer[] box : boundingBoxes) {
                    // reuse variables
                    center_x = box[0];
                    center_y = box[1];

                    if (!assignedBoxes.contains(box)) {  // skip already assigned boxes
                        circleRadius = Math.round(box[2] + box[3] / 2);  // approximate circle from rectangle dimensions

                        prox = Math.pow(Math.abs(animal.x - center_x) ^ 2 + Math.abs(animal.y - center_y) ^ 2, 0.5);

                        if (prox < min_prox) {
                            min_prox = prox;
                            closestBox = box;   // new Integer[]{center_x, center_y, circleRadius};
                        }
                    }
                }

                if (numbs_match && closestBox != null) {   // This means min_prox < displacement_thresh?
                    // todo: instead of min_prox --> use (Decision tree? / Markov? / SVM? / ???) to determine if the next point is reasonable
                    animal.updateLocation(closestBox[0], closestBox[1], current_time_pos);
                    assignedBoxes.add(closestBox);

                } else if (closestBox != null) {
                    System.out.println("First if-statement");
                    animal.updateLocation(closestBox[0], closestBox[1], current_time_pos);
                    assignedBoxes.add(closestBox);

                } else {
                    System.out.println("Predicting trajectory goes here?");
                    animal.updateLocation(animal.x, animal.y, current_time_pos);

                }

                // TODO   put drawing of each animal's circle in here???

                if (DRAW_CIRCLES) {
                    // info : http://bytedeco.org/javacpp-presets/opencv/apidocs/org/bytedeco/javacpp/opencv_imgproc.html#method.detail

                    circle(videoFrameMat, new Point(animal.x, animal.y), circleRadius, new Scalar(0, 255, 0, 1));

                    int thickness;
                    linePointsArr = animal.getLinePointsAsArray();
                    for (int i = 1; i < linePointsArr.length; i++) {
                        // if (linePointsArr[i] == null || linePointsArr[i-1] == null) { continue; }   // check for null values
                        thickness = (int) Math.round(Math.sqrt(lineThicknessBuffer / (i + 1)) * 2);  // todo what does this do?
                        line(videoFrameMat,
                                new Point(((int[]) linePointsArr[i - 1])[0], linePointsArr[i - 1][1]),
                                new Point(linePointsArr[i][0], linePointsArr[i][1]),
                                animal.color, thickness, LINE_4, 0); // thickness, line type, shift     //LINE_4, LINE_8, or LINE_AA
                    }
                }

            }

            /*todo**************************************************************************/
            imshow("test video display", videoFrameMat);

            char key = (char) waitKey(msDelay);
            if (key == 27) { // Escape key to exit
                grabber.release();
                destroyAllWindows();
                break;
            }
            /*todo**************************************************************************/

        }
        grabber.release();
        destroyAllWindows();
    }

    public static void main(String[] args) throws FrameGrabber.Exception {
        String vidpath = "/home/alex/Documents/sproj/videos/IRTestVid2.mp4";
        int n_objs = 0;
        int[] crop = new int[]{1,2,3,4};
        String cfg = "", wgts = "", meta = "", filename = "";
        boolean display = true;
        int sig_figs = 0;

//        Tracker tracker = new Tracker();
//        tracker.run(vidpath, n_objs, crop, cfg, wgts, meta, filename, display, sig_figs);
    }
}