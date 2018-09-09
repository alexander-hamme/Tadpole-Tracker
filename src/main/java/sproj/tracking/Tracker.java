package sproj.tracking;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import sproj.util.DetectionsParser;
import sproj.util.Logger;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import java.io.IOException;
import java.util.ArrayList;

abstract class Tracker {          //  TODO make this an interface?

    static final Logger logger = new Logger();   // LogManager.getLogger("SinglePlateTracker");

    final double DISPL_THRESH_FRACT = 1.5;      // used for distance thresholding
    final int DISPL_THRESH = 15;
    final int ARRAY_MAX_SIZE = 60;              // buffer size of array to accumulate data
    final int frame_resize_width = 720;
    boolean DRAW_SHAPES = true;
    boolean DRAW_RECTANGLES = false;
    int circleRadius = 5;

    String CANVAS_NAME;

    final int IMG_WIDTH = YOLOModelContainer.IMG_WIDTH;
    final int IMG_HEIGHT = YOLOModelContainer.IMG_HEIGHT;
    int INPUT_FRAME_WIDTH;
    int INPUT_FRAME_HEIGHT;

    int WINDOW_WIDTH = 720;     // ask user for size
    int WINDOW_HEIGHT = 720;     // ask user for size

//    protected YOLOModelContainer yoloModelContainer = new YOLOModelContainer();
//    protected ArrayList<Animal> animals = new ArrayList<>();
//
//    private DetectionsParser detectionsParser = new DetectionsParser();
//    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

    protected int numb_of_anmls;     // TODO remove this, since it would number of 'dishes' in MultiPlate??

    FFmpegFrameGrabber grabber;
    CanvasFrame canvasFrame;        // the main CanvasFrame object to update graphics with.
                                    // Not to be instantiated by tracker. A CanvasFrame instance will be passed from outside App class.

    abstract void createAnimalObjects();


    public void tearDown() {
        try {
            grabber.release();
        } catch (FrameGrabber.Exception ignored) {
        }
    }


    abstract void trackVideo(String videoPath) throws Exception;
}
