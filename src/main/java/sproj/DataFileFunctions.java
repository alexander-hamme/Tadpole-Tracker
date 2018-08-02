package sproj;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DataFileFunctions {


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
