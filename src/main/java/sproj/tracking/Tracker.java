package sproj.tracking;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;


import sproj.util.Logger;
import sproj.yolo.YOLOModelContainer;

import java.io.IOException;
import java.util.Iterator;

import static org.bytedeco.javacpp.opencv_imgproc.circle;
import static org.bytedeco.javacpp.opencv_imgproc.line;
import static org.opencv.imgproc.Imgproc.LINE_AA;

public abstract class Tracker {          //  TODO make this an interface?

    static final Logger logger = new Logger();   // LogManager.getLogger("SinglePlateTracker");

    final double DISPL_THRESH_FRACT = 1.5;      // used for distance thresholding
    final int DISPL_THRESH = 15;
    final int ARRAY_MAX_SIZE = 60;              // buffer size of array to accumulate data
    final int frame_resize_width = 720;
    boolean DRAW_RECTANGLES = true; //false;    // uwef
    boolean DRAW_ANML_TRACKS;
    int circleRadius = 5;

    protected final int NUMB_FRAMES_FOR_INIT = 10;    // allow tracking system to find and attach Animal objects to detections

    String CANVAS_NAME;

    final int IMG_WIDTH = YOLOModelContainer.IMG_WIDTH;
    final int IMG_HEIGHT = YOLOModelContainer.IMG_HEIGHT;
    int INPUT_FRAME_WIDTH;
    int INPUT_FRAME_HEIGHT;
    protected int videoFrameRate;

    int WINDOW_WIDTH = 720;     // ask user for size
    int WINDOW_HEIGHT = 720;     // ask user for size

    protected FFmpegFrameGrabber grabber;


//    protected YOLOModelContainer yoloModelContainer = new YOLOModelContainer();
//    protected ArrayList<Animal> animals = new ArrayList<>();
//
//    private DetectionsParser detectionsParser = new DetectionsParser();
//    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

    int numb_of_anmls;     // TODO remove this, since it would number of 'dishes' in MultiPlate??


//    abstract void initializeFrameGrabber(String videoPath) throws FrameGrabber.Exception;


    public abstract Frame timeStep() throws IOException;


    public void tearDown() {
        try {
            grabber.release();
        } catch (FrameGrabber.Exception ignored) {
        }
    }

    protected abstract void createAnimalObjects();

    protected abstract void createAnimalFiles(String baseFilePrefix) throws IOException;

    protected void initializeFrameGrabber(String videoPath) throws FrameGrabber.Exception {
        avutil.av_log_set_level(avutil.AV_LOG_QUIET);       // Suppress verbose FFMPEG metadata output to console
        grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();    // open video file
        videoFrameRate = (int) grabber.getVideoFrameRate();
    }

    /**
     * Note that these drawing functions change the Mat object by changing color values to draw the shapes.
     * @param videoFrameMat Mat object
     * @param animal Animal object
     */
    protected void traceAnimalOnFrame(opencv_core.Mat videoFrameMat, Animal animal, double scaleMultiplier) {
        // info : http://bytedeco.org/javacpp-presets/opencv/apidocs/org/bytedeco/javacpp/opencv_imgproc.html#method.detail

        opencv_core.Scalar circleColor = animal.color; //new Scalar(0,255,0,1);

        if (scaleMultiplier == 1.0) {
            circle(videoFrameMat, new opencv_core.Point(animal.x, animal.y),
                    animal.CIRCLE_RADIUS, circleColor
            );
        } else {
            circle(videoFrameMat, new opencv_core.Point(
                            (int) Math.round(scaleMultiplier * animal.x),
                            (int) Math.round(scaleMultiplier * animal.y)),
                    animal.CIRCLE_RADIUS, circleColor
            );
        }

        // draw trailing trajectory line behind current animal
        int lineThickness = animal.LINE_THICKNESS;
        Iterator<int[]> linePointsIterator = animal.getLinePointsIterator();

        if (linePointsIterator.hasNext()) {

            int[] pt1 = linePointsIterator.next();
            int[] pt2;

            while (linePointsIterator.hasNext()) {

                pt2 = linePointsIterator.next();
                // lineThickness = Math.round(Math.sqrt(animal.LINE_THICKNESS / (animal.LINE_POINTS_SIZE - i)) * 2);


                if (scaleMultiplier != 1.0) {
                    line(
                        videoFrameMat,
                        new opencv_core.Point(
                                (int) Math.round(pt1[0] * scaleMultiplier), (int) Math.round(pt1[1] * scaleMultiplier)),
                        new opencv_core.Point(
                                (int) Math.round(pt2[0] * scaleMultiplier), (int) Math.round(pt2[1] * scaleMultiplier)),
                        animal.color, lineThickness, LINE_AA, 0     // lineThickness, line type, shift
                    );
                } else {

                    line(
                        videoFrameMat,
                        new opencv_core.Point(pt1[0], pt1[1]),
                        new opencv_core.Point(pt2[0], pt2[1]),
                        animal.color, lineThickness, LINE_AA, 0     // lineThickness, line type, shift
                    );
                }

                pt1 = pt2;                                           // -->  line type is LINE_4, LINE_8, or LINE_AA
            }
        } else {
            logger.warn("Line points iterator is empty, failed to draw trajectory paths.");
        }
    }
}