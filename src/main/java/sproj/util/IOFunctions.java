package sproj.util;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sproj.tracking.Animal;
import sproj.szaza_yolo_tensorflow.model.ObjectDetector;

import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class IOFunctions {
    private final static Logger LOGGER = LoggerFactory.getLogger(IOFunctions.class);
    private IOFunctions() {}

    public static byte[] readAllBytesOrExit(final String fileName) throws IOException {
        try {
            return IOUtils.toByteArray(ObjectDetector.class.getResourceAsStream(fileName));
        } catch (IOException | NullPointerException ex) {
            LOGGER.error("Failed to read [{}]!", fileName);
            throw new IOException("Failed to read [" + fileName + "]!", ex);
        }
    }

    public static List<String> readAllLinesOrExit(final String filename) throws IOException {
        try {
            File file = new File(ObjectDetector.class.getResource(filename).toURI());
            return Files.readAllLines(file.toPath(), Charset.forName("UTF-8"));
        } catch (IOException | URISyntaxException ex) {
            LOGGER.error("Failed to read [{}]!", filename, ex.getMessage());
            throw new IOException("Failed to read [" + filename + "]!", ex);
        }
    }

    public static void createDirIfNotExists(final File directory) {
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    public static String getFileName(final String path) {
        return path.substring(path.lastIndexOf("/") + 1, path.length());
    }

    static void writeData(String fileName, ArrayList<Animal> anmls, String header, int sigFigs) throws IOException {

        BufferedWriter bw = Files.newBufferedWriter(Paths.get(fileName));

        if (fileName.contains(".csv")) {
            CSVPrinter out = new CSVPrinter(bw, CSVFormat.DEFAULT);
            out.printRecord(header);

            int startIdx, endIdx;

            for (Animal anml : anmls) {

//                out.printRecord(Arrays.toString(anml.color));
                startIdx = anml.dataPoints.size() - Animal.BUFF_INDEX;
                endIdx = anml.dataPoints.size();

                if (startIdx < 0) { continue; }

                for (int i=startIdx; i<endIdx; i++) {
                    out.printRecord(anml.dataPoints.get(i));
                }
                out.println();  // two blank lines
                out.println();
            }
            out.close();
        } else {

        }

        bw.close();
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

