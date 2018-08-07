package sproj.yolo_porting_attempts;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.conf.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.modelimport.keras.KerasLayer;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.layers.convolutional.KerasSpaceToDepth;
import org.deeplearning4j.nn.transferlearning.FineTuneConfiguration;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TestDL4J {



    private void TinyYoloPrediction() {
//        try {
//            preTrained = (ComputationGraph) new TinyYOLO().initPretrained();
//            prepareLabels();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }



    public static void rewriteModel(String fileName) throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {

        double[][] priorBoxes = { {1.08,1.19},  {3.42,4.41},  {6.63,11.38},  {9.42,5.11},  {16.62,10.52}};
        int seed = 7;
        int iterations = 1;

        KerasLayer.registerCustomLayer("Lambda", KerasSpaceToDepth.class);

        ComputationGraph graph = KerasModelImport.importKerasModelAndWeights(fileName, false);

        INDArray priors = Nd4j.create(priorBoxes);

        FineTuneConfiguration fineTuneConf = new FineTuneConfiguration.Builder()
                .seed(seed)
//                ..iterations(iterations)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
                .gradientNormalizationThreshold(1.0)
                .updater(new Adam.Builder().learningRate(1e-3).build())
                .l2(0.00001)
                .activation(Activation.IDENTITY)
                .trainingWorkspaceMode(WorkspaceMode.ENABLED)
                .inferenceWorkspaceMode(WorkspaceMode.ENABLED)
                .build();

        ComputationGraph model = new TransferLearning.GraphBuilder(graph)
                .fineTuneConfiguration(fineTuneConf)
                .addLayer("outputs",
                    new Yolo2OutputLayer.Builder()
                        .boundingBoxPriors(priors)
                        .build(),
        "conv2d_9")
                .setOutputs("outputs")
                .build();

        System.out.println(model.summary(InputType.convolutional(416, 416, 3)));

        ModelSerializer.writeModel(model, "tiny-yolo-voc_dl4j_inference.v1.zip", false);
    }

    /*
    public static void main1(String[] args) throws IOException {

        INDArray img = null;

        // todo         https://github.com/jesuino/java-ml-projects/blob/master/utilities/yolo-dl4j/src/main/java/org/fxapps/ml/deeplearning/yolo/YOLOModel.java

        String modelFileName = "";  //     need to convert the weights & meta file into a DL4j model??
        double threshold = 0.6;

        YOLO2.builder();

        ComputationGraph yoloModel = ModelSerializer.restoreComputationGraph(modelFileName);

        INDArray output = yoloModel.outputSingle(img);
        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) yoloModel.getOutputLayer(0);
        outputLayer.getPredictedObjects(output, threshold);
    }

    */
    public static void main(String[] args) throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {

        String fileName = "/home/alex/Documents/coding/java/Sproj/src/main/resources/yolo_files/yolo_tadpole.h5";

        rewriteModel(fileName);

        YOLOModel yoloModel = new YOLOModel();
        List<DetectedObject> detectedObjects = yoloModel.detect(new File("resources/images/test_image.png"), 0.7);
    }
}
