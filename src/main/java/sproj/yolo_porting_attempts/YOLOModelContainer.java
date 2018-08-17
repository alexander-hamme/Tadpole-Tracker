package sproj.yolo_porting_attempts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.factory.Nd4j;
import sproj.util.DetectionsParser;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

/**
 *
 */

public class YOLOModelContainer {

    private static final Logger logger = LogManager.getLogger("YOLOModelContainer");   // Todo just have one logger, the Main's?


//    Logger logger = LoggerFactory.getLogger(YOLOModelContainer.class);

    // it should probably be hardcoded like this.
    public final String[] CLASSES = {"tadpole"};

    private final int WINDOW_WIDTH = 900;           // TODO  these things need to go in a separate display class
    private final int WINDOW_HEIGHT = 600;

    public static final int IMG_WIDTH = 416;        // reshaping constraints to set on input
    public static final int IMG_HEIGHT = 416;
    private final int IMG_CHANNELS = 3;

    private int INPUT_FRAME_WIDTH;
    private int INPUT_FRAME_HEIGHT;

    // TODO move this to a separate class or file, e.g. a json doc
    private final String MODEL_FILE_PATH = "src/main/resources/inference/yolo2_dl4j_tad.zip";
    private FFmpegFrameGrabber grabber;
    private OpenCVFrameConverter frameConverter;
    private NativeImageLoader imageLoader = new NativeImageLoader(IMG_HEIGHT, IMG_WIDTH, IMG_CHANNELS);

    // an ImagePreProcessingScaler object, which is instantiated once outside the main loop instead of making a new instance every iteration.
    private ImagePreProcessingScaler normalizingScaler = new ImagePreProcessingScaler(0, 1);
    ;
    private DetectionsParser detectionsParser = new DetectionsParser();

    private ComputationGraph yoloModel;
    private Yolo2OutputLayer outputLayer;

    private CanvasFrame canvas;

    public static final double[][] DEFAULT_PRIOR_BOXES = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};

    private int[] inputShape = {1, IMG_CHANNELS, IMG_WIDTH, IMG_HEIGHT};

    private double CONF_THRESHOLD = 0.5;

    private int nBoxes = 5;
    private double[][] priorBoxes = DEFAULT_PRIOR_BOXES;

    private String layerOutputsName = "conv2d_22";
    private long seed = 1234L;
    private int numClasses = 1;
    private WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
//    private ConvolutionLayer.AlgoMode cudnnAlgoMode = ConvolutionLayer.AlgoMode.PREFER_FASTEST;

    private int[] cropDimensions = {550, 160, 500, 500};        // todo  -->  user can manually drag this at the beginning

    public YOLOModelContainer() throws IOException {

        try {
            yoloModel = ModelSerializer.restoreComputationGraph(new File(MODEL_FILE_PATH));
        } catch (FileNotFoundException e) {
            throw new IOException("Invalid file path to model: " + MODEL_FILE_PATH, e);
        } catch (IOException e) {
            throw new IOException("Model file could not be restored: " + MODEL_FILE_PATH, e);
        }

        logger.info("Warming up model...");
        warmupModel(10);
        outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
        //        System.out.println(yoloModel.summary());
    }

    /**
     * Justification for running warm-up iterations before using the model for inference
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

        // todo time these iterations

        long seed = 12345L; // for reproducibility

        INDArray warmupArray = Nd4j.rand(inputShape, seed);  // TODO   check if these dimensions are right??

        for (int i = 0; i < iterations; i++) {
            yoloModel.outputSingle(warmupArray);
        }
    }

    /**
     * Passing a Frame object to the asMatrix() function results in it being converted to a Mat object anyway,
     * so it is more efficient to have only one Frame to Mat conversion happening in each iteration of the main program.
     * That is the reasoning behind having this function take the converted Mat object instead of the original Frame.
     *
     * @param image
     * @return
     * @throws IOException
     */
    public List<DetectedObject> detectImg(Mat image) throws IOException {

        INDArray imgArr = imageLoader.asMatrix(image);
        normalizingScaler.transform(imgArr);
        INDArray output = yoloModel.outputSingle(imgArr);
        return outputLayer.getPredictedObjects(output, CONF_THRESHOLD);
    }


    /**
     * Video path is either a file or a camera device index, e.g. "0"
     *
     * @param videoPath
     * @throws FrameGrabber.Exception
     */
    private void setUpDisplay(String videoPath) throws FrameGrabber.Exception {

        // TODO  this should be in a different class

        canvas = new CanvasFrame("blah", 1.0);               // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());
        // canvas.setCanvasSize(IMG_WIDTH, IMG_HEIGHT);                    // WINDOW_WIDTH, WINDOW_HEIGHT);

        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.

        grabber = new FFmpegFrameGrabber(videoPath);                       // OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(fileName);
        grabber.start();    // open video file

        INPUT_FRAME_WIDTH = grabber.getImageWidth();
        INPUT_FRAME_HEIGHT = grabber.getImageHeight();

    }

    private void setUpUtilities() {

        // todo these should all be in a separate class
        detectionsParser = new DetectionsParser();
        frameConverter = new OpenCVFrameConverter.ToMat();
        normalizingScaler = new ImagePreProcessingScaler(0, 1);
        imageLoader = new NativeImageLoader(IMG_HEIGHT, IMG_WIDTH, IMG_CHANNELS);

    }
}