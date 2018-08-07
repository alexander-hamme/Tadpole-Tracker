package sproj.yolo_porting_attempts;

import lombok.Builder;
import lombok.Getter;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;

import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.YoloUtils;
import org.deeplearning4j.nn.modelimport.keras.KerasLayer;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.layers.convolutional.KerasSpaceToDepth;
import org.deeplearning4j.nn.transferlearning.FineTuneConfiguration;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.util.ModelSerializer;
import org.deeplearning4j.zoo.model.YOLO2;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.IUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */

public class YOLOModel {

    Logger logger = LoggerFactory.getLogger(YOLOModel.class);

    private final String[] CLASSES = { "tadpole" };

    private final int INPUT_WIDTH = 608;
    private final int INPUT_HEIGHT = 608;
    private final int INPUT_CHANNELS = 3;
    private final String MODEL_FILE_NAME = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo_tadpole.h5";
    private ComputationGraph yoloModel;
    private NativeImageLoader imageLoader;

    public static final double[][] DEFAULT_PRIOR_BOXES = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};

    @Builder.Default @Getter private int nBoxes = 5;
    @Builder.Default @Getter private double[][] priorBoxes = DEFAULT_PRIOR_BOXES;

    private String layerOutputsName = "conv2d_22";
    @Builder.Default private long seed = 1234;
    @Builder.Default private int[] inputShape = {3, 608, 608};
    @Builder.Default private int numClasses = 0;
    @Builder.Default private IUpdater updater = new Adam(1e-3);
    @Builder.Default private CacheMode cacheMode = CacheMode.NONE;
    @Builder.Default private WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
    @Builder.Default private ConvolutionLayer.AlgoMode cudnnAlgoMode = ConvolutionLayer.AlgoMode.PREFER_FASTEST;


    public YOLOModel() {

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

    public List<DetectedObject> detect(File input, double threshold) {
        INDArray img;
        try {
            img = loadImage(input);
        } catch (IOException e) {
            throw new Error("Not able to load image from: " + input.getAbsolutePath(), e);
        }

        INDArray array;
        //warm up the model
        for(int i =0; i< 10; i++) {
            array = yoloModel.outputSingle(img);
        }

        long start = System.currentTimeMillis();
        INDArray output = yoloModel.outputSingle(img);
        long end = System.currentTimeMillis();
        logger.info("simple forward took :" + (end - start));

        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);

        List<DetectedObject> predictedObjects = outputLayer.getPredictedObjects(output, threshold);
        for (DetectedObject detectedObject : predictedObjects) {
            System.out.println(CLASSES[detectedObject.getPredictedClass()]);
        }

        return predictedObjects;
    }

    private INDArray loadImage(File imgFile) throws IOException {
        INDArray image = imageLoader.asMatrix(imgFile);
        ImagePreProcessingScaler scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        return image;
    }

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

        private BoundingBox(double[] topLeft, double[] bottomRight) {
            this.topleftX = topLeft[0];
            this.topleftY = topLeft[1];
            this.botRightX = bottomRight[0];
            this.botRightY = bottomRight[1];
        }

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

        double topLeftX, topLeftY, botRightX, botRightY;


        //todo    do the math?

        for (DetectedObject object : detections) {

            // convert from grid cell units to pixels
            centerX = object.getCenterX() * pixelsPerCell;
            centerY = object.getCenterX() * pixelsPerCell;
            width = object.getWidth() * pixelsPerCell;
            height = object.getHeight() * pixelsPerCell;


            boundingBoxes.add(
                    new BoundingBox(object.getTopLeftXY(), object.getBottomRightXY())
            );
        }

        return boundingBoxes;
    }


    private void convertYAD2KWeights(String filename) throws InvalidKerasConfigurationException, IOException, UnsupportedKerasConfigurationException {
      KerasLayer.registerCustomLayer("Lambda", KerasSpaceToDepth.class);
      ComputationGraph graph = KerasModelImport.importKerasModelAndWeights(filename, false);
      INDArray priors = Nd4j.create(priorBoxes);

      FineTuneConfiguration fineTuneConf = new FineTuneConfiguration.Builder()
                             .seed(seed)
                             .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                             .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
                             .gradientNormalizationThreshold(1.0)
                             .updater(new Adam.Builder().learningRate(1e-3).build())
                             .l2(0.00001)
                             .activation(Activation.IDENTITY)
                             .trainingWorkspaceMode(workspaceMode)
                             .inferenceWorkspaceMode(workspaceMode)
                             .build();

      ComputationGraph model = new TransferLearning.GraphBuilder(graph)
                             .fineTuneConfiguration(fineTuneConf)
                             .addLayer("outputs",
                                     new org.deeplearning4j.nn.conf.layers.objdetect.Yolo2OutputLayer.Builder()  // different class with same name
                                             .boundingBoxPriors(priors)
                                             .build(),
                      "conv2d_22")
              .setOutputs("outputs")
                             .build();

      System.out.println(model.summary(InputType.convolutional(608, 608, 3)));

      ModelSerializer.writeModel(model, "yolo2_dl4j_tad.zip", false);
    }


    public static void main(String[] args) throws IOException, InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {

        String dl4jModel = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo2_dl4j_tad.zip";
        double iouThreshold = 0.4;  //todo  ???

        /*
        String model_weights = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo_tad.h5";
        YOLOModel model = new YOLOModel();
        model.convertYAD2KWeights(model_weights);
        */

        YOLOModel trainedModel = new YOLOModel(dl4jModel);
        List<DetectedObject> detections = trainedModel.detect(new File("/home/alex/Documents/coding/java/Sproj/src/main/resources/images/test_image.png"), 0.5);

        List<BoundingBox> boundingBoxes = trainedModel.parseDetections(detections, iouThreshold);


        boundingBoxes.forEach(boundingBox -> System.out.println(boundingBox.toString()));
        detections.forEach(detectedObject -> System.out.println(detectedObject.toString()));

    }
}
