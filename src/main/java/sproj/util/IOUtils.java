package sproj.util;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
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
import sproj.tracking.Animal;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class IOUtils {

    private final static Logger LOGGER = LoggerFactory.getLogger(IOUtils.class);
    private IOUtils() {}

    public static INDArray loadImage(File imgFile) throws IOException {
        INDArray image = new NativeImageLoader().asMatrix(imgFile);
        ImagePreProcessingScaler scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        return image;
    }


    public static void createDirIfNotExists(final File directory) {
        if (!directory.exists()) {
            directory.mkdir();
        }
    }


    public static List<Double> stringsToDoubles(List<String> lst) {
        // return Lists.transform(lst, input -> Double.valueOf(input));
        return lst.stream().map(Double::valueOf).collect(Collectors.toList());
    }


    static void writeData(String fileName, ArrayList<Animal> anmls, String header, int sigFigs) throws IOException {

        BufferedWriter bw = Files.newBufferedWriter(Paths.get(fileName));

        if (fileName.contains(".csv")) {
            CSVPrinter out = new CSVPrinter(bw, CSVFormat.DEFAULT);
            out.printRecord(header);

            int startIdx, endIdx;

            for (Animal anml : anmls) {

//                double[][] dataPoints = anml.getDataPointsIterator();

                double[][] dataPoints = null;


                Iterator<double[]> dataPointsIterator = anml.getDataPointsIterator();

//                out.printRecord(Arrays.toString(anml.color));
                startIdx = dataPoints.length - Animal.DATA_BUFFER_ARRAY_SIZE;
                endIdx = dataPoints.length;

                if (startIdx < 0) { continue; }

                for (int i=startIdx; i<endIdx; i++) {
                    out.printRecord(
                            (double[]) dataPoints[i]
                    );
                }
                out.println();  // two blank lines
                out.println();
            }
            out.close();
        } else {

        }

        bw.close();
    }

    public static String getFileName(final String path) {
        return path.substring(path.lastIndexOf("/") + 1, path.length());
    }

    private void convertYAD2KWeights(String fileName, String saveName) throws InvalidKerasConfigurationException, IOException, UnsupportedKerasConfigurationException {

        // to be run on an .h5 weights file, and output a zip file

        int nBoxes = 5;
        double[][] priorBoxes = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};

        String layerOutputsName = "conv2d_22";
        long seed = 1234;
         int[] inputShape = {3, 608, 608};      // todo is this right or wrong?
         int numClasses = 0;
         IUpdater updater = new Adam(1e-3);
         CacheMode cacheMode = CacheMode.NONE;
         WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
         ConvolutionLayer.AlgoMode cudnnAlgoMode = ConvolutionLayer.AlgoMode.PREFER_FASTEST;

        KerasLayer.registerCustomLayer("Lambda", KerasSpaceToDepth.class);
        ComputationGraph graph = KerasModelImport.importKerasModelAndWeights(fileName, false);
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
                        layerOutputsName)
                .setOutputs("outputs")
                .build();

        System.out.println(model.summary(InputType.convolutional(608, 608, 3)));

        ModelSerializer.writeModel(model, saveName, false);
    }



    private void saveNewYoloModel(String modelFilePath) throws IOException {
        try {

            File modelFile = new File(modelFilePath);
            ComputationGraph yoloModel;

            if (!modelFile.exists()) {
                System.out.println("Cached model does NOT exists");
                yoloModel = (ComputationGraph) YOLO2.builder().build().initPretrained();
                yoloModel.save(modelFile);
            } else {
                System.out.println("Cached model does exists");
                yoloModel = ModelSerializer.restoreComputationGraph(modelFile);
            }

        } catch (IOException e) {
            throw new IOException("Not able to init the model", e);
        }
    }


    public static void writeAnimalPointsToFile
            (List<Animal> animals, String fileName, boolean appendIfFileExists) throws IOException  {

        try (FileWriter writer = new FileWriter(fileName, appendIfFileExists)) {

            for (Animal animal : animals) {

                Iterator<double[]> pointsIterator = animal.getDataPointsIterator();

                writer.write(String.format("Animal Number %d, RGBA color label: %s\n", animals.indexOf(animal)+1, animal.color.toString()));
                while (pointsIterator.hasNext()) {
                    double[] point = pointsIterator.next();
                    writer.write(point[0] + "," + point[1] + "\n");
                }
                if (animals.indexOf(animal) < animals.size()-1) {    // add newline after all but the last animal
                    writer.write("\n");
                }
            }
        }
    }


    public static void writeDataToFile(List<Double> dataPoints, String fileName, String separator) throws IOException {

        try (FileWriter writer = new FileWriter(fileName)) {
            for(Double point: dataPoints) {
                writer.write(point.toString() + separator);
            }
        }
    }


    public static List<String> readLinesFromFile(String fileName) throws IOException {

        List<String> lines = new ArrayList<>();

        LineIterator it = FileUtils.lineIterator(new File(fileName), "UTF-8");
        try {
            while (it.hasNext()) {
                lines.add(it.nextLine());
            }
        } finally {
            LineIterator.closeQuietly(it);
        }
        return lines;
    }



    static HashedMap<String, List<String>> parseArgs(String[] args) {

        HashedMap<String, List<String>> parameters = new HashedMap<>();
        ArrayList<String> options = null;

        for (String arg : args) {

            if (arg.charAt(0) == '-') {     // option argument
                if (arg.length() < 2) {
                    throw new IllegalArgumentException("Invalid Argument: " + arg);
                }
                options = new ArrayList<>();
                parameters.put(arg, options);
            } else if (options != null) {   // data argument
                options.add(arg);
            } else {
                throw new IllegalArgumentException("Invalid parameters: " + arg);
            }
        }
        return parameters;
    }
}

