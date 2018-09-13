package sproj.util;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import sproj.tracking.SinglePlateTracker;
import sproj.tracking.Tracker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


public class JavaFxAppTest extends Application {

    int n_objs;
    boolean drawShapes;
    int[] crop;
    CanvasFrame canvasFrame;
    String videoPath;


    private Image fxImgFrame;
    private final ImageView iv = new ImageView(fxImgFrame);

    final int WIDTH = 600;
    final int HEIGHT = 400;

    private Java2DFrameConverter paintConverter = new Java2DFrameConverter();

    private Tracker trackerProgram;     // could be either SinglePlate or MultiPlate tracker class


    public JavaFxAppTest() throws IOException {
        getValues();
        trackerProgram = new SinglePlateTracker(n_objs, drawShapes, crop, canvasFrame, videoPath);
    }

//    public void init() throws Exception {
//    }

    private void getValues() {
        // input from user
        n_objs = 5;
        drawShapes = true;
        // video file IMG_3086  -->  {60,210,500,500}
        // video file IMG_3085  -->  550, 160, 500, 500
        crop = new int[]{60,210,500,500};
        canvasFrame = new CanvasFrame("Tracker");
        videoPath = "src/main/resources/videos/IMG_3086.MOV";
    }

    private void timeStep() {

    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("AnimatedImage Example");

        Group root = new Group();
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        root.getChildren().add(iv);


        stage.setScene(scene);

//        Canvas canvas = new Canvas(512, 512);
//        root.getChildren().add(canvas);
//
//        GraphicsContext gc = canvas.getGraphicsContext2D();


//        final long startNanoTime = System.nanoTime();

        AnimationTimer timer = new AnimationTimer() {

//            Image fxImgFrame = new Image(new File("src/main/resources/images/test_image.png").toURI().toString());

            @Override
            public void handle(long currentNanoTime) {


                timeStep();
                try {
                    fxImgFrame = convert(trackerProgram.timeStep());  // new Image(new File("src/main/resources/images/test_image.png").toURI().toString());  //
                    iv.setImage(fxImgFrame);
                } catch (Exception e) {
                    e.printStackTrace();
                    trackerProgram.tearDown();
                    stop();
                }

//                gc.drawImage(fxImgFrame, 0, 0);
            }
        };

        timer.start();
        stage.show();
    }


    private Image convert(org.bytedeco.javacv.Frame frame) {
        return SwingFXUtils.toFXImage(paintConverter.convert(frame), null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}


