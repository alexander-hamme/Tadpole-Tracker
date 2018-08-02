package sproj;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Tensor;
import sproj.model.BoxPosition;
import sproj.model.Recognition;
import sproj.util.GraphBuilder;
import sproj.util.IOFunctions;
import sproj.util.ImageUtil;

import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.Session;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;

import static sproj.Config.MEAN;
import static sproj.Config.SIZE;
import static sproj.util.ImageUtil.displayImage;
import static sproj.util.ImageUtil.saveImage;

/**
 * ObjectDetector class to detect objects using pre-trained models with TensorFlow Java API.
 */
public class ObjectDetector {
    private byte[] GRAPH_DEF;
    private List<String> LABELS;
    private final static Logger logger = LoggerFactory.getLogger(ObjectDetector.class);

    public ObjectDetector() {
        try {
            GRAPH_DEF = IOFunctions.readAllBytesOrExit(Config.GRAPH_FILE);
            LABELS = IOFunctions.readAllLinesOrExit(Config.LABEL_FILE);
        } catch (IOException ex) {
            logger.error("Error reading model files: " + Config.GRAPH_FILE + ", " + Config.LABEL_FILE);
        }
    }

    /**
     * Detect objects on the given image
     * @param imageLocation the location of the image
     */
    public void detect(final String imageLocation) throws IOException {
        byte[] image = IOFunctions.readAllBytesOrExit(imageLocation);
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = new YOLOClassifier().classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            printToConsole(recognitions);
            labelImage(image, recognitions, IOFunctions.getFileName(imageLocation));
        }
    }

    /**
     * Detect objects on the given image
     */
    public void detect(final byte[] image) throws IOException {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = new YOLOClassifier().classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            printToConsole(recognitions);
            labelImage(image, recognitions);
        }
    }

    /**
     * Label image with classes and predictions given by the ThensorFLow
     * @param image buffered image to label
     * @param recognitions list of recognized objects
     */
    public static void labelImage(final byte[] image, final List<Recognition> recognitions, final String fileName) throws IOException {
        BufferedImage bufferedImage = ImageUtil.createImageFromBytes(image);
        float scaleX = (float) bufferedImage.getWidth() / (float) SIZE;
        float scaleY = (float) bufferedImage.getHeight() / (float) SIZE;
        Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();

        for (Recognition recognition: recognitions) {
            BoxPosition box = recognition.getScaledLocation(scaleX, scaleY);
            //draw text
            graphics.drawString(recognition.getTitle() + " " + recognition.getConfidence(), box.getLeft(), box.getTop() - 7);
            // draw bounding box
            graphics.drawRect(box.getLeftInt(),box.getTopInt(), box.getWidthInt(), box.getHeightInt());
        }

        graphics.dispose();
        saveImage(bufferedImage, Config.OUTPUT_DIR + "/" + fileName);
    }

    /**
     * Label image with classes and predictions given by the TensorFLow
     * @param image buffered image to label
     * @param recognitions list of recognized objects
     */
    public static void labelImage(final byte[] image, final List<Recognition> recognitions) throws IOException {
        BufferedImage bufferedImage = ImageUtil.createImageFromBytes(image);
        float scaleX = (float) bufferedImage.getWidth() / (float) SIZE;
        float scaleY = (float) bufferedImage.getHeight() / (float) SIZE;
        Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();

        for (Recognition recognition: recognitions) {
            BoxPosition box = recognition.getScaledLocation(scaleX, scaleY);
            //draw text
            graphics.drawString(recognition.getTitle() + " " + recognition.getConfidence(), box.getLeft(), box.getTop() - 7);
            // draw bounding box
            graphics.drawRect(box.getLeftInt(),box.getTopInt(), box.getWidthInt(), box.getHeightInt());
        }

        graphics.dispose();
        displayImage(bufferedImage);
    }

    /**
     * Pre-process input. It resize the image and normalize its pixels
     * @param imageBytes Input image
     * @return Tensor<Float> with shape [1][416][416][3]
     */
    private Tensor<Float> normalizeImage(final byte[] imageBytes) {
        try (Graph graph = new Graph()) {
            GraphBuilder graphBuilder = new GraphBuilder(graph);

            final Output<Float> output =
                    graphBuilder.div( // Divide each pixels with the MEAN
                            graphBuilder.resizeBilinear( // Resize using bilinear interpolation
                                    graphBuilder.expandDims( // Increase the output tensors dimension
                                            graphBuilder.cast( // Cast the output to Float
                                                    graphBuilder.decodeJpeg(
                                                            graphBuilder.constant("input", imageBytes), 3),
                                                    Float.class),
                                            graphBuilder.constant("make_batch", 0)),
                                    graphBuilder.constant("size", new int[]{SIZE, SIZE})),
                            graphBuilder.constant("scale", MEAN));

            try (Session session = new Session(graph)) {
                return session.runner().fetch(output.op().name()).run().get(0).expect(Float.class);
            }
        }
    }

    /**
     * Executes graph on the given preprocessed image
     * @param image preprocessed image
     * @return output tensor returned by tensorFlow
     */
    private float[] executeYOLOGraph(final Tensor<Float> image) {
        try (Graph graph = new Graph()) {
            graph.importGraphDef(GRAPH_DEF);
            try (Session s = new Session(graph);
                 Tensor<Float> result = s.runner().feed("input", image).fetch("output").run().get(0).expect(Float.class)) {
                float[] outputTensor = new float[new YOLOClassifier().getOutputSizeByShape(result)];
                FloatBuffer floatBuffer = FloatBuffer.wrap(outputTensor);
                result.writeTo(floatBuffer);
                return outputTensor;
            }
        }
    }

    /**
     * Prints out the recognize objects and its confidence
     * @param recognitions list of recognitions
     */
    private void printToConsole(final List<Recognition> recognitions) {
        for (Recognition recognition : recognitions) {
            logger.info("Object: {} - confidence: {}", recognition.getTitle(), recognition.getConfidence());
        }
    }
}