package sproj.util;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
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

import java.awt.image.BufferedImage;
import java.io.IOException;

// Animation of Earth rotating around the sun. (Hello, world!)
public class TestImageUpdatingApp extends Application {

    private Java2DFrameConverter paintConverter = new Java2DFrameConverter();

    private Tracker trackerProgram;     // could be either SinglePlate or MultiPlate tracker class


    public TestImageUpdatingApp(int n_objs, boolean drawShapes, int[] crop, CanvasFrame canvasFrame, String videoPath) throws IOException{
        trackerProgram = new SinglePlateTracker(n_objs, drawShapes, crop, canvasFrame, videoPath);
    }


    @Override
    public void start(Stage stage) {
        stage.setTitle( "AnimatedImage Example" );

        Group root = new Group();
        Scene scene = new Scene(root);
        stage.setScene(scene);

        Canvas canvas = new Canvas(512, 512);
        root.getChildren().add(canvas);

        GraphicsContext gc = canvas.getGraphicsContext2D();


//        final long startNanoTime = System.nanoTime();

        new AnimationTimer() {

            Image fxImgFrame = null;


            public void handle(long currentNanoTime) {

                try {
                    fxImgFrame = convert(trackerProgram.timeStep());
                } catch (IOException e) {
                    e.printStackTrace();
                    trackerProgram.tearDown();
                    stop();
                }

                gc.drawImage(fxImgFrame, 0, 0 );
            }
        }.start();

        stage.show();
    }


    private Image convert(org.bytedeco.javacv.Frame frame) {
        return SwingFXUtils.toFXImage(paintConverter.convert(frame), null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}