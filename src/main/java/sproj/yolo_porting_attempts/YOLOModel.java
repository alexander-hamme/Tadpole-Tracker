package sproj.yolo_porting_attempts;

import lombok.Builder;
import lombok.Getter;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import org.datavec.image.loader.NativeImageLoader;

import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;

import org.deeplearning4j.zoo.model.YOLO2;
import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.layers.objdetect.YoloUtils;

import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.util.ModelSerializer;

import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.IUpdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sproj.util.IOFunctions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;

import static org.bytedeco.javacpp.opencv_imgproc.CV_AA;
import static org.bytedeco.javacpp.opencv_imgproc.rectangle;

/**
 *
 */

public class YOLOModel {

    Logger logger = LoggerFactory.getLogger(YOLOModel.class);

    private final String[] CLASSES = { "tadpole" };

    private final int WINDOW_WIDTH = 900;           // TODO  these things need to go in a separate display class
    private final int WINDOW_HEIGHT = 600;

    private final int IMG_WIDTH = 608;        // reshaping constraints to set on input
    private final int IMG_HEIGHT = 608;
    private final int IMG_CHANNELS = 3;

    // TODO move this to a separate class or file, e.g. a json doc
    private final String MODEL_FILE_NAME = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo_tadpole.h5";
    private ComputationGraph yoloModel;
    private NativeImageLoader imageLoader;

    private CanvasFrame canvas;
    private String canvasCaption = "Tracker";

    public static final double[][] DEFAULT_PRIOR_BOXES = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};

    @Builder.Default @Getter private int nBoxes = 5;
    @Builder.Default @Getter private double[][] priorBoxes = DEFAULT_PRIOR_BOXES;

    private String layerOutputsName = "conv2d_22";
    @Builder.Default private long seed = 1234;
    @Builder.Default private int[] inputShape = {1, 3, IMG_WIDTH, IMG_HEIGHT};
    @Builder.Default private int numClasses = 0;
    @Builder.Default private IUpdater updater = new Adam(1e-3);
    @Builder.Default private CacheMode cacheMode = CacheMode.NONE;
    @Builder.Default private WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
    @Builder.Default private ConvolutionLayer.AlgoMode cudnnAlgoMode = ConvolutionLayer.AlgoMode.PREFER_FASTEST;


    public YOLOModel() {
        imageLoader = new NativeImageLoader(IMG_WIDTH, IMG_HEIGHT, IMG_CHANNELS);
    }

    public YOLOModel(String filename) throws IOException {
        try {
            File modelFile = new File(filename);
            if (!modelFile.exists()) {
                logger.info("Cached model does NOT exists");
                yoloModel = (ComputationGraph) YOLO2.builder().build().initPretrained();
                yoloModel.save(modelFile);
            } else {
                logger.info("Cached model does exists");
                yoloModel = ModelSerializer.restoreComputationGraph(modelFile);
            }

            imageLoader = new NativeImageLoader(IMG_WIDTH, IMG_HEIGHT, IMG_CHANNELS);
        } catch (IOException e) {
            throw new IOException("Not able to init the model", e);
        }
    }

    /** Explanation adopted from https://deeplearning4j.org/benchmark

     Justification for running warm-up iterations before using the model for inference:

     A warm-up period is where you run a number of iterations (for example, a few hundred) of your benchmark without timing,
     before commencing timing for further iterations.

     Why is a warm-up required? The first few iterations of any ND4J/DL4J execution may be slower than those that come later,
     for a number of reasons:

     In the initial benchmark iterations, the JVM has not yet had time to perform just-in-time compilation of code.
     Once JIT has completed, code is likely to execute faster for all subsequent operations ND4J and DL4J
     (and, some other libraries) have some degree of lazy initialization: the first operation may trigger
     some one-off execution code. DL4J or ND4J (when using workspaces) can take some iterations
     to learn memory requirements for execution.

     During this learning phase, performance will be lower than after its completion.
     */
    private void warmupModel(int iterations) {

        // todo time these iterations

        long seed = 12345L; // for reproducibility

        INDArray warmupArray = Nd4j.rand(new int[]{1, IMG_CHANNELS, IMG_WIDTH, IMG_HEIGHT}, seed);  // TODO   check if these dimensions are right??

        for (int i=0; i<iterations; i++) {
            yoloModel.outputSingle(warmupArray);
        }
    }

    /**
     * Passing a Frame object to the asMatrix() function results in it being converted to a Mat object anyway,
     * so it is more efficient to have only one Frame to Mat conversion happening
     * in each iteration of the main program. That is why this function takes a Mat object instead of a Frame.
     *
     * Also, a single ImagePreProcessingScaler object is instantiated outside the main loop instead of
     * making a new instance every iteration.
     * @param image
     * @param threshold
     * @return
     * @throws IOException
     */
    private List<DetectedObject> detectImg(opencv_core.Mat image, double threshold, ImagePreProcessingScaler scaler) throws IOException {

        INDArray imgArr = imageLoader.asMatrix(image);
        scaler.transform(imgArr);
        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
        INDArray output = yoloModel.outputSingle(imgArr);
        System.out.println(yoloModel.summary());
        return outputLayer.getPredictedObjects(output, threshold);
    }

    // The Frame gets converted to a Mat object anyway, better to only have this conversion run once.
//    private List<DetectedObject> detectFrame(org.bytedeco.javacv.Frame videoFrame, double threshold) throws IOException {
//
//        INDArray imgArr = imageLoader.asMatrix(videoFrame);
//        // Note that this converts it to a
//        ImagePreProcessingScaler scaler = new ImagePreProcessingScaler(0, 1);
//        scaler.transform(imgArr);
//        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
//        INDArray output = yoloModel.outputSingle(imgArr);
//        return outputLayer.getPredictedObjects(output, threshold);
//    }


    private class BoundingBox {
        // todo  decide which to use:  floats?  ints?  doubles?
        // todo OR don't use a separate class if it takes too much memory / time?
        private int topleftX;
        private int topleftY;
        private int botRightX;
        private int botRightY;

        private BoundingBox(int x1, int y1, int x2, int y2) {
            this.topleftX = x1;
            this.topleftY = y1;
            this.botRightX = x2;
            this.botRightY = y2;
        }

        public String toString() {
            return String.format(
                    "Detection at (%d, %d)topleft, (%d, %d)bottomright",
                    this.topleftX, this.topleftY, this.botRightX, this.botRightY);
        }
    }

    /**
     * A detected object, by an object detection algorithm. Note that the dimensions (for center X/Y, width/height)
     * depend on the specific implementation. For example, in the Yolo2OutputLayer, the dimensions are grid cell units-
     * for example, with 416x416 input, 32x downsampling, we have 13x13 grid cells (each corresponding to 32 pixels in the input image).
     *
     * Thus, a centerX of 5.5 would be xPixels=5.5x32 = 176 pixels from left. Widths and heights are similar:
     * in this example, a with of 13 would be the entire image (416 pixels), and a height of 6.5 would be 6.5/13 = 0.5 of the image (208 pixels).
     *
     * @param detections
     * @return
     */
    private List<BoundingBox> parseDetections(List<DetectedObject> detections, double iouThreshold) {

        List<BoundingBox> boundingBoxes = new ArrayList<>(detections.size());

        System.out.println(String.format("currently there are %s boxes detected", detections.size()));

        YoloUtils.nms(detections, iouThreshold);            // apply non maxima suppression (NMS)  todo  does this work well enough?

        System.out.println(String.format("now there are %s boxes detected", detections.size()));

        double numberOfGridCells = 13.0;
        double pixelsPerCell = (double) IMG_WIDTH / numberOfGridCells;   // 32.0;       // assumes 1:1 image aspect ratio

        double centerX, centerY;
        double width, height;

        int topLeftX, topLeftY, botRightX, botRightY;


        for (DetectedObject object : detections) {

            // convert from grid cell units to pixels
            centerX = object.getCenterX() * pixelsPerCell;
            centerY = object.getCenterX() * pixelsPerCell;
            width = object.getWidth() * pixelsPerCell;
            height = object.getHeight() * pixelsPerCell;

            topLeftX = (int) Math.round(centerX - (width / 2));
            topLeftY = (int) Math.round(centerY - (height / 2));
            botRightX = (int) Math.round(centerX + (width / 2));
            botRightY = (int) Math.round(centerY + (height / 2));

            boundingBoxes.add(
                    new BoundingBox(topLeftX, topLeftY, botRightX, botRightY)
            );
        }

        return boundingBoxes;
    }

    private void setUpDisplay() {
        // Create image window named "My Image".
        canvas = new CanvasFrame(canvasCaption, 1.0);       // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());

//        canvas.setCanvasSize(IMG_WIDTH, IMG_HEIGHT);      // WINDOW_WIDTH, WINDOW_HEIGHT);
        // Exit application when window is closed.
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }


    private void trackVideo(String fileName, double confThresh) throws FrameGrabber.Exception, InterruptedException, IOException {

        double iouThreshold = 0.5;

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(fileName);
//        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(fileName);
        OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

        ImagePreProcessingScaler normalizingScaler = new ImagePreProcessingScaler(0, 1);

        grabber.start(); // open video file

        List<BoundingBox> boundingBoxes;
        List<DetectedObject> predictedObjects;

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {

            // TODO     switch to org.opencv.core functions  ???

            opencv_core.Mat frameImg = frameConverter.convertToMat(frame);

            predictedObjects = detectImg(frameImg, confThresh, normalizingScaler);

            for (DetectedObject detectedObject : predictedObjects) {
                System.out.println(CLASSES[detectedObject.getPredictedClass()]);
            }

            boundingBoxes = parseDetections(predictedObjects, iouThreshold);

            for (BoundingBox box : boundingBoxes) {

                org.bytedeco.javacpp.opencv_imgproc.rectangle(frameImg, new opencv_core.Point(box.topleftX, box.topleftY),
                        new opencv_core.Point(box.botRightX, box.botRightY), opencv_core.Scalar.RED, 1, CV_AA, 0);
            }

            System.out.println("Canvas Dimensions:" + canvas.getCanvasSize().toString());
            System.out.println("IMG size: " + frameImg.size().toString());


            canvas.showImage(frame);
            Thread.sleep(10L);
        }
        grabber.release();
    }


    public static void main(String[] args)
            throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException, InterruptedException {

        String dl4jModel = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo2_dl4j_tad.zip";
        double iouThreshold = 0.4;  //todo  ???

        /*
        String model_weights = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo_tad.h5";
        YOLOModel model = new YOLOModel();
        model.convertYAD2KWeights(model_weights);
        */

//        new YOLOModel().trackVideo("/home/alex/Documents/coding/java/Sproj/src/main/resources/videos/IRTestVid2.mp4");
//        "/home/alex/Documents/coding/java/Sproj/src/main/resources/videos/IMG_3085.MOV"


        YOLOModel trainedModel = new YOLOModel(dl4jModel);


        if (1==0) {
            return;
        }

        trainedModel.setUpDisplay();
        trainedModel.warmupModel(10);
        trainedModel.trackVideo("/home/alex/Documents/coding/java/Sproj/src/main/resources/videos/IRTestVid2.mp4", 0.5);

//        List<BoundingBox> boundingBoxes = trainedModel.parseDetections(detections, iouThreshold);
//        boundingBoxes.forEach(boundingBox -> System.out.println(boundingBox.toString()));
//        detections.forEach(detectedObject -> System.out.println(detectedObject.toString()));

        /**
         *
        boundingBoxes.forEach(boundingBox ->
            cvRectangle(grabbedImage, cvPoint(x, y), cvPoint(x+w, y+h), opencv_core.CvScalar.RED, 1, CV_AA, 0);
        )
         */
    }
}
