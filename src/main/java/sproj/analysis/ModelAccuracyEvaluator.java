package sproj.analysis;


import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.tracking.Tracker;
import sproj.util.DetectionsParser;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to test the accuracy of the underlying Yolo model
 *
 * Calculate metrics of how frequently  the model correctly
 * find all subjects of interest in each frame of a video feed
 */
public class ModelAccuracyEvaluator {

    private FFmpegFrameGrabber grabber;
    private YOLOModelContainer yoloModelContainer;
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();


    private void initializeGrabber(String videoPath) throws FrameGrabber.Exception {
        grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();        // open video file
    }

    public ModelAccuracyEvaluator() throws IOException {
        yoloModelContainer = new YOLOModelContainer();
    }

    public void evaluateModelOnVideo(String videoPath, int numbAnimals, Rect cropRectangle) throws IOException {

        initializeGrabber(videoPath);
        evaluateOnVideo(numbAnimals, cropRectangle);


    }


    private List<Double> evaluateOnVideo(double numbAnimals, Rect cropRect) throws IOException {

        List<DetectedObject> detectedObjects;
        List<Double> detectionAccuracies = new ArrayList<>(grabber.getLengthInVideoFrames());   // store one detection accuracy record per frame

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {

            detectedObjects = yoloModelContainer.runInference(new Mat(frameConverter.convertToMat(frame), cropRect));
            detectionAccuracies.add(detectedObjects.size() / numbAnimals);

        }

    }


    private void tearDown() {
        try {
            grabber.close();
        } catch (FrameGrabber.Exception ignored) {
        }
    }
}
