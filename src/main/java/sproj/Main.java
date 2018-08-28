package sproj;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacv.CanvasFrame;
import sproj.tracking.Tracker;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {

    private final int CANVAS_WIDTH = 700;
    private final int CANVAS_HEIGHT = 700;

    static final Logger logger = LogManager.getLogger("Main");

    private String canvasCaption = "Tadpole Tracker";


    // todo should things like IMG_WIDTH be originally set here?
    private CanvasFrame canvas;
    private Tracker tadpoleTracker;

    private int NUMBER_OF_OBJECTS_TO_TRACK;
    private final boolean showDisplay = true;

    private int[] cropDimensions;
    private String videoPath;       // this will get passed in?

    // not to be instantiated
    private Main(){
    }

    private void setUp() throws IOException {
        getInputDataFromUser();         // // TODO: 8/12/18  this has to go first to set the value of   n_objs
        setUpDisplay();
        setUpUtilities();
        loadVideoFromFile();
    }


    private void setUpDisplay() {

        // TODO  this should be in a different class

        canvas = new CanvasFrame(canvasCaption, 1.0);               // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());
         canvas.setCanvasSize(CANVAS_WIDTH, CANVAS_HEIGHT);                    // WINDOW_WIDTH, WINDOW_HEIGHT);

        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
        canvas.setResizable(true);

//        canvas.getContentPane().add();
        canvas.pack();
        canvas.setVisible(showDisplay);

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

    /**
     * This will have input text fields for the user to enter information into.
     * In addition, the user will be able to select a region of the video to crop to,
     * which will automatically set the values of cropDimensions.
     */
    private void getInputDataFromUser() {

        NUMBER_OF_OBJECTS_TO_TRACK = 5;
        // video file IMG_3086  -->  {60,210,500,500}
        // video file IMG_3085  -->  550, 160, 500, 500
        cropDimensions = new int[]{60,210,500,500};
    }

    private void setUpUtilities() throws IOException {
        tadpoleTracker = new Tracker(NUMBER_OF_OBJECTS_TO_TRACK, showDisplay);

    }


    private void runTracker() throws IOException, InterruptedException {
        tadpoleTracker.trackVideo(videoPath, cropDimensions, canvas);
    }

    private void tearDown() {
        canvas.dispose();
    }

    public void main(String[] args) throws IOException, InterruptedException, Exception {

        try {
            setUp();
            runTracker();
        } catch (IOException e) {
            // todo    specific error handling, etc
            logger.error(e);
        } finally {
            tearDown();
        }
    }
}
