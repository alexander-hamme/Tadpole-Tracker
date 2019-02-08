package sproj.util;

import com.google.common.base.Joiner;
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
import sproj.tracking.Animal;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A collection of public static I/O functions
 */
public abstract class IOUtils {

    /**
     * Load image as INDArray (n dimensional array format used by nd4j library)
     * @param imgFile  String
     * @return INDArray
     * @throws IOException
     */
    public static INDArray loadImage(File imgFile) throws IOException {
        INDArray image = new NativeImageLoader().asMatrix(imgFile);
        ImagePreProcessingScaler scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);
        return image;
    }

    public static List<Double> stringsToDoubles(List<String> lst) {
        return lst.stream().map(Double::valueOf).collect(Collectors.toList());
    }

    /**
     * Write animal data points to CSV file, with one animal per column
     *
     * Note: by the time this function is called, the file `baseFileName` + ".csv"
     * has already been created with headers by the createAnimalCSVFiles()
     * function in the constructor of the SinglePlateTracker class
     *
     * @param animals list of animal objects
     * @param baseFileName  String, filename without ".csv" extension
     * @param appendIfFileExists boolean
     * @throws IOException
     */
    public static void writeAnimalsToCSV(List<Animal> animals, String baseFileName,
                        boolean appendIfFileExists) throws IOException  {

        // TODO: put timestamp with each point

        try (PrintWriter writer = new PrintWriter(
                new FileWriter(baseFileName + ".csv", appendIfFileExists))
        ) {

            // Create list of iterators so that points can be added in
            // adjacent columns in the CSV file
            List<Iterator<double[]>> anmlPtsIterators = new ArrayList<>();

            for (Animal animal : animals) {
                anmlPtsIterators.add(animal.getDataPointsIterator());
            }

            StringBuilder sb = new StringBuilder();

            boolean empty = false;  // animal iterators have the exact same number of data points,
                                    // so if one is empty, they are all empty
            while (! empty) {

                for (Iterator<double[]> it : anmlPtsIterators) {

                    if (it.hasNext()) {
                        // Arrays.toString() uses commas to delimit array elements,
                        // which is a problem for writing to CSV values.
                        // this separates the values using a vertical bar
                        double[] pt = it.next();
                        //                 time position (ms), x, y, calculated data correctness
                        //                 none of these values have non-zero decimals except the last
                        sb.append(String.format("%.0f|%.0f|%.0f|%.3f,",pt[0], pt[1], pt[2], pt[3]));
                    } else {
                        empty = true;
                        break;
                    }
                }

                sb.append("\n");   // fine for linux, but change to writer.println() for cross platform compatibility

                writer.write(sb.toString());

                // clear the buffer for reuse instead of reallocating memory with `new StringBuilder()`
                // since the size of each string will be almost exactly the same, there is little risk
                // of memory leakage, and this method is far more efficient, because garbage collection
                // doesn't need to clean the discarded StringBuilder memory each iteration of the loop
                sb.setLength(0);
            }
        }
    }

    /**
     * Unused, but available in case there is a need for each animal's
     * data to be written to a separate file
     * @param animals
     * @param filePrefix
     * @param appendIfFileExists
     * @param clearPoints
     * @throws IOException
     */
    public static void writeAnimalPointsToSeparateFiles(List<Animal> animals, String filePrefix,
                                                        boolean appendIfFileExists, boolean clearPoints) throws IOException {

        for (Animal animal : animals) {

            String saveName = filePrefix + "_anml" + animals.indexOf(animal) + ".dat";

            try (FileWriter writer = new FileWriter(saveName, appendIfFileExists)) {

                Iterator<double[]> pointsIterator = animal.getDataPointsIterator();

                while (pointsIterator.hasNext()) {
                    double[] point = pointsIterator.next();
                    writer.write(point[0] + "," + point[1] + "," + point[2] + "\n");
                }

                if (clearPoints) {
                    animal.clearPoints();
                }

            }

        }
    }


    /**
     * Function used by the FrameLabeler class for manually labeling tadpoles
     * in video frames and saving the points to file
     *
     * @param nestedArrs
     * @param fileName
     * @param separator
     * @param append
     * @param <T>
     * @throws IOException
     */
    public static <T> void writeNestedObjArraysToFile(List<T[][]> nestedArrs, String fileName, String separator,
                                                boolean append) throws IOException {

        try (FileWriter writer = new FileWriter(fileName, append)) {

            int idx = 0;
            int size = nestedArrs.size();

            for(T[][] arr: nestedArrs) {

                int i = 0;
                int sz = arr.length;

                for (T[] obj : arr) {

                    String toWrite = Arrays.toString(obj);

                    if (i++ != sz - 1) {        // don't write separator at end of line
                        writer.write(toWrite + separator);
                    } else {
                        writer.write(toWrite);
                    }
                }
                /*if (idx++ != size - 1) {        // don't write newline at end of file
                    writer.write("\n");
                }*/
                writer.write("\n");
            }
        }

    }

    /**
     * Function used by the FrameLabeler class
     * @param objArrays
     * @param fileName
     * @param separator
     * @param append
     * @param <T>
     * @throws IOException
     */
    public static <T> void writeObjArraysToFile(List<T[]> objArrays, String fileName, String separator,
                                                boolean append) throws IOException {

        try (FileWriter writer = new FileWriter(fileName, append)) {

            int idx = 0;
            int size = objArrays.size();

            for(T[] arr: objArrays) {

                int i = 0;
                int sz = arr.length;

                for (T obj : arr) {

                    String toWrite = obj.toString();

                    if (i++ != sz - 1) {        // don't write separator at end of line
                        writer.write(toWrite + separator);
                    } else {
                        writer.write(toWrite);
                    }
                }
                /*if (idx++ != size - 1) {        // don't write newline at end of file
                    writer.write("\n");
                }*/
                writer.write("\n");
            }
        }
    }

    /**
     * Function used by the FrameLabeler class
     * @param objects
     * @param fileName
     * @param separator
     * @param append
     * @param <T>
     * @throws IOException
     */
    public static <T> void writeObjectsToFile(List<T> objects, String fileName, String separator,
                                          boolean append) throws IOException {
        try (FileWriter writer = new FileWriter(fileName, append)) {
            int idx = 0;
            int size = objects.size();
            for(T point: objects) {
                String toWrite = point.toString();
                if (idx++ != size-1){        // don't write newline at end of file
                    writer.write(toWrite + separator);
                } else {
                    writer.write(toWrite);
                }
            }
        }
    }

    public static void writeLinesToFile(List<String> lines, String fileName, String separator,
                                        boolean append) throws IOException {

        try (FileWriter writer = new FileWriter(fileName, append)) {
            int idx = 0;
            int size = lines.size();
            for(String point: lines) {
                if (idx++ != size-1){        // don't write newline at end of file
                    writer.write(point + separator);
                } else {
                    writer.write(point);
                }
            }
        }
    }

    public static void writeDataToFile(List<Double> dataPoints, String fileName, String separator, boolean append) throws IOException {

        try (FileWriter writer = new FileWriter(fileName, append)) {
            int idx = 0;
            int sze = dataPoints.size();
            for(Double point: dataPoints) {
                if (idx++ != sze-1){        // don't write newline at end of file
                    writer.write(point.toString() + separator);
                } else {
                    writer.write(point.toString());
                }
            }
        }
    }

    /**
     * Parses the training output file generated by Joseph Redmon's darknet framework
     */
    public static void readYoloTrainingLog(String fileName, String saveName) throws IOException {

        List<String> loss = new ArrayList<>();

        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.forEach(line -> {

                if (line.contains("images")) {

                    String n1 = line.split(":")[0];
                    String n2 = line.split(",")[1].trim().split(" ")[0];

                    loss.add(n1 + "," + n2);
                }
            });
        }

        writeLinesToFile(loss, saveName, "\n", false);
    }


    public static List<String> readInLargeFile(File file) throws IOException {

        List<String> lines = new ArrayList<>();

        try (LineIterator it = FileUtils.lineIterator(file, "UTF-8")) {
            while (it.hasNext()) {
                lines.add(it.nextLine());
            }
        }
        return lines;
    }


    public List<String> readTextFile(String textFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(textFile))) {
            stream.forEach(lines::add);
        }
        return lines;
    }

    /**
     * Intended for parsing a long list of args containing both flags and data argument.,
     * Maps flags as keys in a hashmap to a list with their respective data arguments
     * @param args
     * @return  Hashmap with flags mapped to their respective data arguments
     */
    public static HashedMap<String, List<String>> parseArgs(String[] args) {

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

    /**
     * Code taken from ByteDeco's github repository
     */
    public static void convertYAD2KWeights(String fileName, String saveName, double[][] priorBoxes) throws InvalidKerasConfigurationException, IOException, UnsupportedKerasConfigurationException {

        // to be run on an .h5 weights file, outputs a zip file

        int nBoxes = 5;

//        double[][] priorBoxes = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};

        String layerOutputsName = "conv2d_23";
        long seed = 1234;
        int[] inputShape = {608, 608, 3};      // todo is this right or wrong?
        int numClasses = 1;
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

        System.out.println(model.summary(InputType.convolutional(inputShape[0],inputShape[1], inputShape[2])));

        ModelSerializer.writeModel(model, saveName, false);
    }


    public static void saveNewYoloModel(String modelFilePath) throws IOException {
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


    public static void main1(String[] args) throws IOException {
        readYoloTrainingLog(
    /*fileName*/ "training/trainingOutput.log",
    /*savename*/ "training/trainingOutputPoints.dat"
                );
    }

    /**
     * Convert a directory full of iteratively named keras model files
     * into deeplearning4j models
     */
    public static void main2(String[] args) throws UnsupportedKerasConfigurationException, IOException, InvalidKerasConfigurationException {
        String modelDir = "/home/ah2166/Documents/darknet/modelConversion/convertedModels";
        String savePath = "/home/ah2166/Documents/sproj/java/Tadpole-Tracker/src/main/resources/inference/";

        int minModel = 10000;
        int maxModel = 18000;

        for (int its = minModel; its <= maxModel; ) {

            String yoloModelFile = String.format("%s/yolo-obj_%d.h5", modelDir, its);

            System.out.println("Converting model " + (its  % minModel / 1000 + 1) + " of " + (maxModel - minModel + 1) / 1000
            + " " + yoloModelFile);


            double[][] priorBoxes = {{1.3221, 1.73145}, {3.19275, 4.00944}, {5.05587, 8.09892}, {9.47112, 4.84053}, {11.2364, 10.0071}};

            convertYAD2KWeights(yoloModelFile,
                    savePath + String.format("yolov2_%d.zip", its), priorBoxes);
            its += 1000;
            //*/
        }
    }
}

