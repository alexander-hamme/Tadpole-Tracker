package sproj;

import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.util.ModelSerializer;
import org.deeplearning4j.zoo.model.YOLO2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class YOLOModel {
    Logger logger = LoggerFactory.getLogger(YOLOModel.class);

    private final String[] CLASSES = { "tadpole" };

    private final int INPUT_WIDTH = 608;
    private final int INPUT_HEIGHT = 608;
    private final int INPUT_CHANNELS = 3;
    private final String MODEL_FILE_NAME = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo_tadpole.h5";
    private ComputationGraph yoloModel;
    private NativeImageLoader imageLoader;

    public YOLOModel() throws IOException {
        try {
            File modelFile = new File(MODEL_FILE_NAME);
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

        //warm up the model
        for(int i =0; i< 10; i++) {
            yoloModel.outputSingle(img);
        }

        long start = System.currentTimeMillis();
        INDArray output = yoloModel.outputSingle(img);
        long end = System.currentTimeMillis();
        logger.info("simple forward took :" + (end - start));

        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
        List<DetectedObject> predictedObjects = outputLayer.getPredictedObjects(output, threshold);
        for (DetectedObject detectedObject : predictedObjects) {
            logger.info(CLASSES[detectedObject.getPredictedClass()]);
        }

        return predictedObjects;
    }

    private INDArray loadImage(File imgFile) throws IOException {
        INDArray image = imageLoader.asMatrix(imgFile);
        ImagePreProcessingScaler scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        return image;
    }
}
