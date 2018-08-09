package sproj.tracking;

import static org.bytedeco.javacpp.opencv_highgui.destroyAllWindows;
import static org.bytedeco.javacpp.opencv_highgui.imshow;
import static org.bytedeco.javacpp.opencv_highgui.waitKey;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.opencv.imgproc.Imgproc.LINE_4;

import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;

import java.util.ArrayList;
import java.util.Arrays;

public class Tracker {

    static int VERBOSITY_LEVEL = 3;             // 1, 2, 3, or 4
    final double DISPL_THRESH_FRACT = 1.5;      // used for distance thresholding
    final int DISPL_THRESH = 80;
    final int ARRAY_MAX_SIZE = 60;              // buffer size of array to accumulate data
    final int frame_resize_width = 720;
    boolean DRAW_CIRCLES = true;

    private ArrayList<Animal> animals;

    public Tracker() {
        int counter = 0;
        int dx = 0, dy = 0;
        this.animals = new ArrayList<>();
    }

    private void setup(int width, int height, int n_objs) {
        int[][] colors = {{1, 1, 1}, {90, 90, 90}, {255, 0, 255}, {0, 255, 255}, {0, 0, 255}, {47, 107, 85},
                {113, 179, 60}, {255, 0, 0}, {255, 255, 255}, {0, 180, 0}, {255, 255, 0}, {160, 160, 160},
                {160, 160, 0}, {0, 0, 0}, {202, 204, 249}, {0, 255, 127}, {40, 46, 78}};

        int x, y;

        for (int i = 0; i < n_objs; i++) {
            x = (int) ((i + 1) / ((double) n_objs * width));
            y = (int) ((i + 1) / ((double) n_objs * height));
            this.animals.add(new Animal(x, y, colors[i]));
        }
    }

    public void run(String videoPath, int n_objs, int[] crop, String cfg, String wgts,
                    String meta, String filename, boolean display, int sig_figs) throws FrameGrabber.Exception {

        this.setup(crop[1] - crop[0], crop[3] - crop[2], n_objs);


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

        Double label;               // = 0.0;
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
                                new Point(linePointsArr[i - 1][0], linePointsArr[i - 1][1]),
                                new Point(linePointsArr[i][0], linePointsArr[i][1]),
                                animal.color, thickness, 0, LINE_4); // thickness, line type, shift     //LINE_4, LINE_8, or LINE_AA
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

        Tracker tracker = new Tracker();
        tracker.run(vidpath, n_objs, crop, cfg, wgts, meta, filename, display, sig_figs);
    }
}