package sproj;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.bytedeco.javacv.Java2DFrameConverter;
import sproj.tracking.MultiPlateTracker;
import sproj.tracking.SinglePlateTracker;
import sproj.tracking.Tracker;
import sproj.util.Logger;

import java.io.IOException;

/**
 * The main app to be run, from which the other classes are called.
 *
 * This is currently functional for displaying tracking but a lot of work remains,
 * i.e. creating a simple interface for non-CS researchers to use
 */
public class TrackerApp extends Application {

    private static final Logger logger = new Logger(); //LogManager.getLogger("TrackerApplication");

    private final String APPLICATION_TITLE = "Tracker";
    private final boolean RESIZABLE = true;

    final int INITIAL_CANVAS_WIDTH = 900;
    final int INITIAL_CANVAS_HEIGHT = 600;

    private int numb_objs_to_track;
    private boolean drawShapes;
    private int[] crop;
    private String videoPath;


    private Image imageFrame;
    private final ImageView imageView = new ImageView();

    private Java2DFrameConverter paintConverter = new Java2DFrameConverter();


    private Tracker trackerProgram;     // could be either SinglePlate or MultiPlate tracker class


    public TrackerApp() throws IOException {
        getValues();
        selectTrackerProgram();
    }

    private void selectTrackerProgram() throws IOException {

        String selected = "singleplate";   // this will be set by user todo

        if (selected == "singleplate") {
            trackerProgram = new SinglePlateTracker(numb_objs_to_track, drawShapes, crop, videoPath, "");
        } else {
            trackerProgram = new MultiPlateTracker(numb_objs_to_track, drawShapes, videoPath);
        }
    }

    /**
     * This will eventually set these values by asking for input from user
     */
    private void getValues() {
        numb_objs_to_track = 5;
        drawShapes = true;

        //***** Note that x + width must be <= original image width, and y + height must be <= original image height**//
        crop = new int[]{60,210,500,500};
            // video file IMG_3086  -->  {60,210,500,500}
            // video file IMG_3085  -->  {550, 160, 500, 500}
        videoPath = "data/videos/IMG_5126.MOV";
    }

    private void setUpGraphics(Stage stage) {

    }

    private void listenInput(Stage stage) {
        //tie the F key to fullscreen:
        /*if (pressed f key){
            stage.setFullScreen(true);
            stage.setFullScreenExitHint("Press esc to exit full screen");
        }*/
    }

    @Override
    public void start(Stage stage) {

        stage.setTitle(APPLICATION_TITLE);

        Group root = new Group();
        Scene scene = new Scene(root, INITIAL_CANVAS_WIDTH, INITIAL_CANVAS_HEIGHT);

        root.getChildren().add(imageView);

        stage.setResizable(RESIZABLE);
        stage.setScene(scene);

//        Canvas canvas = new Canvas(512, 512);
//        root.getChildren().add(canvas);
//
//        GraphicsContext gc = canvas.getGraphicsContext2D();

//        final long startNanoTime = System.nanoTime();

        //Image imageFrame;

        // todo Click Start button to call a function to do this?
        AnimationTimer timer = new AnimationTimer() {

            @Override
            public void handle(long currentNanoTime) {

                try {
                    imageFrame = convert(trackerProgram.timeStep());  // new Image(new File("src/main/resources/images/test_image.png").toURI().toString());  //
                    if (imageFrame != null) {/* logger.info("Reached end of video")*/
                        imageView.setImage(imageFrame);
                    } else {
                        trackerProgram.tearDown();
                        stop();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    trackerProgram.tearDown();
                    stop();
                }
            }
        };
        // todo Click button to start timer
        timer.start();
        stage.show();
    }


    private Image convert(org.bytedeco.javacv.Frame frame) {
        if (frame == null) return null;
        return SwingFXUtils.toFXImage(paintConverter.convert(frame), null);
    }

    public static void main(String[] args) {
        try {
            Platform.setImplicitExit(true);
            TrackerApp.launch(args);
        } catch (Exception e) {
            //logger.error(e);
            //etc etc
            e.printStackTrace();
        } finally {
            Platform.exit();
//            System.exit(ERRNO);
        }
    }
}
