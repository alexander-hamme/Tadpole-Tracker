package sproj.yolo;


import org.bytedeco.javacpp.opencv_core.Mat;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.factory.Nd4j;
import sproj.util.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;


//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
import sproj.TrackerApp;


//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

/**
 *
 */

public class YOLOModelContainer {

//    private final String modelFilePath = "src/main/resources/inference/yolov2_10000its.zip";
    //private static final String DEFAULT_MODEL_PATH = "src/main/resources/inference/yolov2_19000.zip";
    private static final String DEFAULT_MODEL_PATH = "src/main/resources/inference/yolov2_80000.zip";

    private final Logger logger = new Logger();//TrackerApp.getLogger();   //  LogManager.getLogger("YOLOModelContainer");   // Todo don't create new instance, share one logger object, eg from Main

//    Logger logger = LoggerFactory.getLogger(YOLOModelContainer.class);

    public static final int IMG_WIDTH = 416;        // reshaping constraints to apply to input images
    public static final int IMG_HEIGHT = 416;
    private final int IMG_CHANNELS = 3;
    private final int[] INPUT_SHAPE = {1, IMG_CHANNELS, IMG_WIDTH, IMG_HEIGHT};
    private final double CONF_THRESHOLD = 0.3;
    private final int WARMUP_ITERATIONS = 10;

    private final NativeImageLoader imageLoader = new NativeImageLoader(IMG_HEIGHT, IMG_WIDTH, IMG_CHANNELS);
    private final ImagePreProcessingScaler normalizingScaler = new ImagePreProcessingScaler(0, 1);
    private ComputationGraph yoloModel;
    private Yolo2OutputLayer outputLayer;

//    private final double[][] DEFAULT_PRIOR_BOXES = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};
//    private int nBoxes = 5;
//    private double[][] priorBoxes = DEFAULT_PRIOR_BOXES;
//    public final String[] CLASSES = {"tadpole"};

//    private String layerOutputsName = "conv2d_22";
//    private long seed = 1234L;
//    private int numClasses = 1;
//    private WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
//    private ConvolutionLayer.AlgoMode cudnnAlgoMode = ConvolutionLayer.AlgoMode.PREFER_FASTEST;


    /**
     * Passing a Frame object to the asMatrix() function results in it being converted to a Mat object anyway,
     * so it is more efficient to have only one Frame to Mat conversion happening in each iteration of the main program loop.
     * That is the reasoning behind having this function take the converted Mat object instead of the original Frame.
     *
     * @param image
     * @return
     * @throws IOException
     */
    public List<DetectedObject> runInference(Mat image) throws IOException {
        INDArray imgArr = imageLoader.asMatrix(image);
        normalizingScaler.transform(imgArr);
        INDArray output = yoloModel.outputSingle(imgArr);
        return outputLayer.getPredictedObjects(output, CONF_THRESHOLD);
    }

    public YOLOModelContainer() throws IOException {
        this(new File(DEFAULT_MODEL_PATH));
    }

    public YOLOModelContainer(File modelFilePath) throws IOException {

        try {
//            logger.info("Loading model...");
            System.out.print("Loading model...");
            yoloModel = ModelSerializer.restoreComputationGraph(modelFilePath);
        } catch (FileNotFoundException e) {
            throw new IOException("Invalid file path to model: " + modelFilePath, e);
        } catch (IOException e) {
            throw new IOException("Model file could not be restored: " + modelFilePath, e);
        }

//        logger.info("Loaded.");
        System.out.print("\rModel loaded.");
        warmupModel(WARMUP_ITERATIONS);
        outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
    }


    /**
     * Justification for running warm-up iterations before using the model for inference:
     *
     * (explanation adopted from https://deeplearning4j.org/benchmark)
     *
     * A warm-up period is where you run a number of iterations (for example, a few hundred) of your benchmark without timing,
     * before commencing timing for further iterations.
     *
     * Why is a warm-up required? The first few iterations of any ND4J/DL4J execution may be slower than those that come later,
     * for a number of reasons:
     *
     * In the initial benchmark iterations, the JVM has not yet had time to perform just-in-time compilation of code.
     * Once JIT has completed, code is likely to execute faster for all subsequent operations. ND4J and DL4J
     * (and, some other libraries) have some degree of lazy initialization: the first operation may trigger
     * some one-off execution code. DL4J or ND4J (when using workspaces) can take some iterations
     * to learn memory requirements for execution.

     * During this initialization phase, performance will be slower than after its completion.
     */
    private void warmupModel(int iterations) {

        // todo time these iterations to verify it does actually speed up

        long seed = 12345L; // for reproducibility

        INDArray warmupArray = Nd4j.rand(INPUT_SHAPE, seed);

        for (int i = 0; i < iterations; i++) {
            yoloModel.outputSingle(warmupArray);
        }
    }
}