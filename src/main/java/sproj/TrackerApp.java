package sproj;

import org.apache.logging.log4j.Logger;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Java2DFrameConverter;
import sproj.tracking.Tracker;
import sproj.util.VideoFrameComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class TrackerApp {

    private final int CANVAS_WIDTH = 700;
    private final int CANVAS_HEIGHT = 700;
    private final boolean SHOW_DISPLAY = true;

    private static final Logger logger = Main.logger;   //   or    LogManager.getLogger("Main");

    private String canvasCaption = "Tracker";


    // todo should things like IMG_WIDTH be originally set here?
    private CanvasFrame canvas;
    private Tracker tadpoleTracker;


    private int[] cropDimensions;           // todo   set these to video dimensions by default
    private String videoPath;       // this will get passed in?
    private int NUMBER_OF_OBJECTS_TO_TRACK;

    public TrackerApp(){
    }

    private void setUp() throws IOException {
        getInputDataFromUser();         //   this has to go first to set the value of   n_objs   for following functions
        setUpDisplay();
        setUpUtilities();
        loadVideoFromFile();
    }

    private void setUpDisplay() {

        // https://www.programcreek.com/java-api-examples/?api=org.bytedeco.javacv.CanvasFrame
        // todo should this be in a different class?


        VideoFrameComponent imageHolder = new VideoFrameComponent();

        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, BoxLayout.PAGE_AXIS);
        panel.setLayout(layout);
        panel.setOpaque(true);

        canvas = new CanvasFrame(canvasCaption, 1.0);               // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());
        canvas.setCanvasSize(CANVAS_WIDTH, CANVAS_HEIGHT);                    // WINDOW_WIDTH, WINDOW_HEIGHT);

        canvas.setLocationRelativeTo(null);     // centers the window
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
        canvas.setResizable(true);
        canvas.setLayout(layout);

        canvas.setContentPane(panel);
//        canvas.getContentPane().add(canvas);
        // add components
//        canvas.getContentPane().add();
//        canvas.getContentPane().add(panel);
        canvas.pack();

        canvas.setVisible(SHOW_DISPLAY);

        /**         TODO    Move these graphic functions to separate class

         canvasFrame.setContentPane(new Container());
         canvasFrame.setIconImage(new Image());                      // convert frame to Graphics Object??
         canvasFrame.setLayeredPane(new JLayeredPane().paint(new Graphics()););
         canvasFrame.setGlassPane();


         Component frameContainer = new Component() {
        @Override
        public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
        return super.imageUpdate(img, infoflags, x, y, w, h);
        }
        };
         canvasFrame.update(new Graphics().create());
         canvasFrame.createImage(new ImageProducer());
         canvasFrame.add("frame", frameContainer);
         */

    }

    /**
     * This will be replaced by a native file selection window,
     * which will then set the video path
     * @throws IOException
     */
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
     * The user will also be able to select a region of the video to crop to,
     * which will automatically set the values of cropDimensions.
     */
    private void getInputDataFromUser() {

        NUMBER_OF_OBJECTS_TO_TRACK = 5;
        // video file IMG_3086  -->  {60,210,500,500}
        // video file IMG_3085  -->  550, 160, 500, 500
        cropDimensions = new int[]{60,210,500,500};
    }

    private void setUpUtilities() throws IOException {
        tadpoleTracker = new Tracker(NUMBER_OF_OBJECTS_TO_TRACK, SHOW_DISPLAY);

    }


    private void runTracker() throws IOException, InterruptedException {
        tadpoleTracker.trackVideo(videoPath, cropDimensions, canvas);
    }

    private void tearDown() {
        canvas.dispose();
    }

    public void run() throws IOException, InterruptedException, Exception {

        setUp();

        try {
            runTracker();
        } catch (IOException e) {
            // todo    specific error handling, etc
            logger.error(e);
        } finally {
            tearDown();
        }
    }
}