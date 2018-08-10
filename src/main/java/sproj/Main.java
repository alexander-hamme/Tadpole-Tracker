package sproj;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FrameGrabber;
import sproj.tracking.Tracker;

import javax.swing.*;
import java.io.IOException;

public class Main {

    private String canvasCaption = "Tadpole Tracker";


    // todo should things like IMG_WIDTH be originally set here?
    private CanvasFrame canvas;
    private Tracker tadpoleTracker;

    private int NUMBER_OF_OBJECTS_TO_TRACK;
    private final boolean showDisplay = true;

    private int[] cropDimensions;
    private String videoPath;       // this will get passed in?

    public Main() throws IOException {
        setUpDisplay();
        setUpUtilities();
    }


    private void setUpDisplay() {

        // TODO  this should be in a different class

        canvas = new CanvasFrame(canvasCaption, 1.0);               // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());
        // canvas.setCanvasSize(IMG_WIDTH, IMG_HEIGHT);                    // WINDOW_WIDTH, WINDOW_HEIGHT);

        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
    }

    private void setUpUtilities() throws IOException {
        tadpoleTracker = new Tracker(NUMBER_OF_OBJECTS_TO_TRACK, showDisplay);
    }


    private void run() throws IOException, InterruptedException {

        NUMBER_OF_OBJECTS_TO_TRACK = 5;
        videoPath = "src/main/resources/videos/IMG_3085.MOV";
        cropDimensions = new int[]{550, 160, 500, 500};

        tadpoleTracker.trackVideo(videoPath, cropDimensions, canvas);
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        Main main = new Main();
        try {
            main.run();
        } catch (Exception e) {
            throw e;
        } finally {
            main.tadpoleTracker.grabber.release();      // todo    tearDown function
        }
    }
}
