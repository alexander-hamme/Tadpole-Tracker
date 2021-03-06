package sproj.tracking;


import org.bytedeco.javacv.Frame;
import sproj.yolo.YOLOModelContainer;

import java.io.IOException;

/**
 * Similar to SinglePlateTracker class, this class will iterate through the input video feed (from a file or a camera device),
 * and implement tracking functions to record the movement data of the subject animals. The main difference is that
 * this is specifically designed for multi-plate tracking, where each animal is in a separate petri dish, which
 * removes the complication of having collisions and occlusions, and also allows constraints to be made on each animal's
 * possible range of location and motion within the overall video frame.
 */
public class MultiPlateTracker extends Tracker {

    private YOLOModelContainer yoloModelContainer;

    public MultiPlateTracker(int n_objs, boolean drawShapes, String videoPath) throws IOException {

        this.numb_of_anmls = n_objs;
        this.DRAW_ANML_TRACKS = drawShapes;
        this.CANVAS_NAME = "Tadpole SinglePlateTracker";

        initializeFrameGrabber(videoPath);      // test if video file is valid and readable first
        createAnimalObjects();
        yoloModelContainer = new YOLOModelContainer();  // load model
    }


    @Override
    public Frame timeStep() throws IOException {
        return null;
    }

    @Override
    protected void createAnimalObjects() {
        // for dish in dishes... assign one animal to the center of the dish
    }

    @Override
    protected void createAnimalFiles(String baseFilePrefix) throws IOException {

    }
}
