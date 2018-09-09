package sproj.tracking;


import org.bytedeco.javacv.CanvasFrame;

/**
 * Similar to SinglePlateTracker class, this class iterates through the input video feed (from a file or a camera device),
 * and implements tracking functions to record the movement data of the subject animals. The main difference is that
 * this is specifically designed for multi-plate tracking, where each animal is in a separate petri dish, which
 * removes the complication of having collisions and occlusions, and also allows constraints to be made on each animal's
 * possible range of location and motion within the overall video frame.
 *
 * The recorded data is intermittently passed to the IOUtils class to be written (or appended) to file.
 */
public class MultiPlateTracker extends Tracker {

    public MultiPlateTracker(int n_objs, boolean drawShapes) {

        this.numb_of_anmls = n_objs;
        this.DRAW_SHAPES = drawShapes;
        this.CANVAS_NAME = "Tadpole SinglePlateTracker";
    }


    @Override
    void createAnimalObjects() {
        // for dish in dishes... assign one animal to the center of the dish
    }

    @Override
    void trackVideo(String videoPath, CanvasFrame canvasFrame) {

    }
}
