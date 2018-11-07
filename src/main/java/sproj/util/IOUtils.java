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
import sproj.tracking.Animal;
import sproj.tracking.AnimalWithFilter;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class IOUtils {

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

    public static void writeAnimalsToSingleFile(List<AnimalWithFilter> animals, String fileName,
                                               boolean appendIfFileExists, boolean clearPoints) throws IOException  {

        // TODO: put timestamp with each point

        try (FileWriter writer = new FileWriter(fileName, appendIfFileExists)) {

            for (AnimalWithFilter animal : animals) {

                Iterator<double[]> pointsIterator = animal.getDataPointsIterator();

                //writer.write(String.format("Animal Number %d, RGBA color label: %s\n", animals.indexOf(animal)+1, animal.color.toString()));
                writer.write(String.format("Animal Number %d|BGRA color label: %s\n", animals.indexOf(animal)+1, animal.color.toString()));
                while (pointsIterator.hasNext()) {
                    double[] point = pointsIterator.next();
                    writer.write(point[0] + "," + point[1] + "," + point[2] + "\n");
//                    writer.write(String.join(",", point) + "\n");
                }
                if (animals.indexOf(animal) < animals.size()-1) {    // add newline after all but the last animal
                    writer.write("\n");
                }

                if (clearPoints) {
                    animal.clearPoints();
                }

            }
        }
    }

    public static void writeAnimalPointsToFile(List<AnimalWithFilter> animals, String filePrefix,
                                               boolean appendIfFileExists, boolean clearPoints) throws IOException {

        for (AnimalWithFilter animal : animals) {

            String saveName = filePrefix + "_anml" + animals.indexOf(animal) + ".dat";

            try (FileWriter writer = new FileWriter(saveName, appendIfFileExists)) {

                Iterator<double[]> pointsIterator = animal.getDataPointsIterator();

                //writer.write(String.format("Animal Number %d, RGBA color label: %s\n", animals.indexOf(animal)+1, animal.color.toString()));
                while (pointsIterator.hasNext()) {
                    double[] point = pointsIterator.next();
                    writer.write(point[0] + "," + point[1] + "," + point[2] + "\n");
//                    writer.write(String.join(",", point) + "\n");
                }

                if (clearPoints) {
                    animal.clearPoints();
                }

            }

        }
    }

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

    public static void readYoloTrainingLog(String fileName, String saveName) throws IOException {

        List<String> loss = new ArrayList<>();

        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.forEach(line -> {

                if (line.contains("images")) {

                    String n1 = line.split(":")[0];
                    String n2 = line.split(",")[1].trim().split(" ")[0];


                    loss.add(n1 + "," + n2);
                    /*try {

                        loss.add(new double[]{
                                Double.parseDouble(n1),
                                Double.parseDouble(n2)
                        });
                    } catch (NumberFormatException ignored) {

                    }*/
                }
            });
        }

        writeLinesToFile(loss, saveName, "\n", false);
    }


    public static List<String> readLinesFromFile(File file) throws IOException {

        List<String> lines = new ArrayList<>();

        try (LineIterator it = FileUtils.lineIterator(file, "UTF-8")) {
            while (it.hasNext()) {
                lines.add(it.nextLine());
            }
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

    private static void convertYAD2KWeights(String fileName, String saveName, double[][] priorBoxes) throws InvalidKerasConfigurationException, IOException, UnsupportedKerasConfigurationException {

        // to be run on an .h5 weights file, and output a zip file

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

    public static void main1(String[] args) throws IOException {
        readYoloTrainingLog(
                "/home/ah2166/Documents/sproj/python/graphing/training/trainingOutput2.log",
                "/home/ah2166/Documents/sproj/python/graphing/training/trainingOutputPoints.dat"
                );
    }

    public static void main(String[] args) throws UnsupportedKerasConfigurationException, IOException, InvalidKerasConfigurationException {
        String modelDir = "/home/ah2166/Documents/darknet/modelConversion/convertedModels";
        String savePath = "/home/ah2166/Documents/sproj/java/Tadpole-Tracker/src/main/resources/inference/";

//        int its = 10000;
//        while (its <= 18000) {
        int minModel = 19000;
        int maxModel = 19000;

        for (int its = minModel; its <= maxModel; ) {

            System.out.println("Converting model " + (its  % minModel / 1000 + 1) + " of " + (maxModel - minModel + 1) / 1000);

//            String yoloModelFile = String.format("%s/%dits/yolov2_%d.h5", modelDir, its, its);
            String yoloModelFile = String.format("%s/yolo-obj_%d.h5", modelDir, its);
            double[][] priorBoxes = {{1.3221, 1.73145}, {3.19275, 4.00944}, {5.05587, 8.09892}, {9.47112, 4.84053}, {11.2364, 10.0071}};

            convertYAD2KWeights(yoloModelFile,
                    savePath + String.format("yolov2_%d.zip", its), priorBoxes);
            its += 1000;
            //*/
        }
    }
}

