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

/**
 * Container class for Yolov2 Model
 */
public class YOLOModelContainer {

    private static final String DEFAULT_MODEL_PATH = "src/main/resources/inference/yolov2_80000.zip";

    private final Logger logger = new Logger();//  LogManager.getLogger("YOLOModelContainer");

    public static final int IMG_WIDTH = 416;        // reshaping constraints to apply to input images
    public static final int IMG_HEIGHT = 416;
    private final int IMG_CHANNELS = 3;
    private final int[] INPUT_SHAPE = {1, IMG_CHANNELS, IMG_WIDTH, IMG_HEIGHT};

    // this is a relatively low confidence threshold,
    // but it allows the model to provide accurate detections more reliably,
    // with false detections being handled by the OptimalAssigner class
    private final double CONF_THRESHOLD = 0.2;

    private final int WARMUP_ITERATIONS = 10;

    private final NativeImageLoader imageLoader = new NativeImageLoader(IMG_HEIGHT, IMG_WIDTH, IMG_CHANNELS);
    private final ImagePreProcessingScaler normalizingScaler = new ImagePreProcessingScaler(0, 1);
    private ComputationGraph yoloModel;
    private Yolo2OutputLayer outputLayer;

    /**
     * This is the function called externally by SinglePlateTracker. Runs inference on
     * the passed in Mat image and returns a List of DetectedObject instances
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
            logger.info("Loading model...");
            yoloModel = ModelSerializer.restoreComputationGraph(modelFilePath);
        } catch (FileNotFoundException e) {
            throw new IOException("Invalid file path to model: " + modelFilePath, e);
        } catch (IOException e) {
            throw new IOException("Model file could not be restored: " + modelFilePath, e);
        }

        logger.info("Model loaded.");
        warmupModel(WARMUP_ITERATIONS);
        outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
    }


    /**
     * Run warm-up iterations before using the model for inference.
     *
     * Explanation of "warming up the model" adopted from https://deeplearning4j.org/benchmark:
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

        long seed = 12345L; // for reproducibility

        INDArray warmupArray = Nd4j.rand(INPUT_SHAPE, seed);

        for (int i = 0; i < iterations; i++) {
            yoloModel.outputSingle(warmupArray);
        }
    }
}