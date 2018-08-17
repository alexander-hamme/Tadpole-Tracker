package sproj;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacv.CanvasFrame;
import sproj.tracking.Tracker;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {

    static final Logger logger = LogManager.getLogger("Main");

    private String canvasCaption = "Tadpole Tracker";


    // todo should things like IMG_WIDTH be originally set here?
    private CanvasFrame canvas;
    private Tracker tadpoleTracker;

    private int NUMBER_OF_OBJECTS_TO_TRACK;
    private final boolean showDisplay = true;

    private int[] cropDimensions;
    private String videoPath;       // this will get passed in?

    public Main() throws IOException {
        getInputDataFromUser();         // // TODO: 8/12/18  this has to go first to set the value of   n_objs
        setUpDisplay();
        setUpUtilities();
        loadVideoFromFile();
    }


    private void setUpDisplay() {

        // TODO  this should be in a different class

        canvas = new CanvasFrame(canvasCaption, 1.0);               // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());
        // canvas.setCanvasSize(IMG_WIDTH, IMG_HEIGHT);                    // WINDOW_WIDTH, WINDOW_HEIGHT);

        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
    }

    private void loadVideoFromFile() throws IOException {
        // etc etc, choose file from file window
        // OR select the check box for webcam  -->  can Java autodetect camera devices?

        videoPath = "src/main/resources/videos/IMG_3086.MOV";

        try {
            assert (new File(videoPath).exists() && new File(videoPath).isFile());
        } catch (AssertionError e) {
            throw new FileNotFoundException("Could not find file: " + videoPath);
        }
    }

    private void getInputDataFromUser() {
        NUMBER_OF_OBJECTS_TO_TRACK = 5;
        // IMG_3086  -->  {60,210,500,500}
        // IMG_3085  -->  550, 160, 500, 500
        cropDimensions = new int[]{60,210,500,500}; //new int[]{550, 160, 500, 500};

    }

    private void setUpUtilities() throws IOException {
        tadpoleTracker = new Tracker(NUMBER_OF_OBJECTS_TO_TRACK, showDisplay);
    }


    private void run() throws IOException, InterruptedException {
        tadpoleTracker.trackVideo(videoPath, cropDimensions, canvas);
    }

    public static void main(String[] args) throws IOException, InterruptedException, Exception {

        Main main = new Main();

        try {
            main.run();
        } catch (Exception e) {
            logger.error(e);
        } finally {
            main.tadpoleTracker.grabber.release();      // TODO    tearDown function
        }
    }
}
