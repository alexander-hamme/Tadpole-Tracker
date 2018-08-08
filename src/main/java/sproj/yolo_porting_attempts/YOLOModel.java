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

/**
 *
 */

public class YOLOModel {

    Logger logger = LoggerFactory.getLogger(YOLOModel.class);

    private final String[] CLASSES = { "tadpole" };

    private final int INPUT_WIDTH = 608;        // TODO: 8/8/18    you should be able to change these as needed
    private final int INPUT_HEIGHT = 608;
    private final int INPUT_CHANNELS = 3;

    //todo move this to a separate file, e.g. a json doc
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
    @Builder.Default private int[] inputShape = {1, 3, INPUT_WIDTH, INPUT_HEIGHT};
    @Builder.Default private int numClasses = 0;
    @Builder.Default private IUpdater updater = new Adam(1e-3);
    @Builder.Default private CacheMode cacheMode = CacheMode.NONE;
    @Builder.Default private WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
    @Builder.Default private ConvolutionLayer.AlgoMode cudnnAlgoMode = ConvolutionLayer.AlgoMode.PREFER_FASTEST;


    public YOLOModel() {
        imageLoader = new NativeImageLoader(INPUT_WIDTH, INPUT_HEIGHT, INPUT_CHANNELS);
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

            imageLoader = new NativeImageLoader(INPUT_WIDTH, INPUT_HEIGHT, INPUT_CHANNELS);
        } catch (IOException e) {
            throw new IOException("Not able to init the model", e);
        }
    }

    private void warmupModel(int iterations) {
        /* Why Warmup?   from  https://deeplearning4j.org/benchmark

        Guideline 1: Run Warm-Up Iterations Before Benchmarking

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
        long seed = 12345L; // for reproducibility

        INDArray warmupArray = Nd4j.rand(new int[]{1, INPUT_CHANNELS, INPUT_WIDTH,INPUT_HEIGHT}, seed);  // TODO   check if these dimensions are right??

        for (int i=0; i<iterations; i++) {
            yoloModel.outputSingle(warmupArray);
        }
    }

    public List<DetectedObject> detectSingleImage(File input, double threshold) {
        INDArray img;
        try {
            img = IOFunctions.loadImage(input);
        } catch (IOException e) {
            throw new Error("Not able to load image from: " + input.getAbsolutePath(), e);
        }

        INDArray array;
        //warm up the model

        System.out.println(Arrays.toString(img.shape()));

        for(int i =0; i< 10; i++) {
            array = yoloModel.outputSingle(img);
        }
        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);

        long start = System.currentTimeMillis();
        INDArray output = yoloModel.outputSingle(img);
        List<DetectedObject> predictedObjects = outputLayer.getPredictedObjects(output, threshold);
        long end = System.currentTimeMillis();
        logger.info("detection took :" + (end - start));


        for (DetectedObject detectedObject : predictedObjects) {
            System.out.println(CLASSES[detectedObject.getPredictedClass()]);
        }

        return predictedObjects;
    }

    private List<DetectedObject> detectImg(opencv_core.Mat image, double threshold) throws IOException {

        INDArray imgArr = imageLoader.asMatrix(image);
        ImagePreProcessingScaler scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(imgArr);
        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
        INDArray output = yoloModel.outputSingle(imgArr);
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
        private double topleftX;
        private double topleftY;
        private double botRightX;
        private double botRightY;

        private BoundingBox(int x1, int y1, int x2, int y2) {
            this.topleftX = x1;
            this.topleftY = y1;
            this.botRightX = x2;
            this.botRightY = y2;
        }

//        private BoundingBox(double[] topLeft, double[] bottomRight) {
//            this.topleftX = topLeft[0];
//            this.topleftY = topLeft[1];
//            this.botRightX = bottomRight[0];
//            this.botRightY = bottomRight[1];
//        }

        public String toString() {
            return String.format(
                    "Detection at topleft: (%4.3f, %4.3f), bottomright: (%4.3f, %4.3f)",
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

//        int numberOfGridCells = 13;
        double pixelsPerCell = 32.0;

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

        //todo  canvas.setCanvasSize(WIDTH, HEIGHT)
        // Exit application when window is closed.
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }


    private void trackVideo(String fileName) throws FrameGrabber.Exception, InterruptedException {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(fileName);
//        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(fileName);
        OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
        grabber.start(); // open video file

        Frame frame;
        while ((frame = grabber.grabImage()) != null) {




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

        trainedModel.setUpDisplay();
        trainedModel.trackVideo("/home/alex/Documents/coding/java/Sproj/src/main/resources/videos/IRTestVid2.mp4");

        if (1==0) {
            return;
        }

        trainedModel.warmupModel(10);

        List<DetectedObject> detections = trainedModel.detect(new File("/home/alex/Documents/coding/java/Sproj/src/main/resources/images/test_image.png"), 0.5);

        List<BoundingBox> boundingBoxes = trainedModel.parseDetections(detections, iouThreshold);


        boundingBoxes.forEach(boundingBox -> System.out.println(boundingBox.toString()));
        detections.forEach(detectedObject -> System.out.println(detectedObject.toString()));

        /**
         *
        boundingBoxes.forEach(boundingBox ->
            cvRectangle(grabbedImage, cvPoint(x, y), cvPoint(x+w, y+h), opencv_core.CvScalar.RED, 1, CV_AA, 0);
        )
         */
    }
}
