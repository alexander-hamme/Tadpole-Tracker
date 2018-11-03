package sproj.analysis;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import static sproj.util.IOUtils.readLinesFromFile;


public abstract class DataAnalysisFunctions {


    public static double computeMean(List<Double> data) {
        return data.stream().reduce(0.0, Double::sum) / data.size();
    }

    public static double standardDeviation(List<Double> data, Double mean) {

        List<Double> squaredDifferences = new ArrayList<>(data.size());

        mean = (mean != null) ? mean : computeMean(data);       // optional parameter to pass precomputed mean

        for (double d : data) {
            squaredDifferences.add(Math.pow(d - mean, 2));
        }

        return Math.pow(computeMean(squaredDifferences), 0.5);
    }

    /**_
     * Calculates confidence intervals for the given data observations
     * @param data list of data to calculate confidence intervals on
     * @param confidence desired confidence
     * @return confidence intervals for data
     */
    public static double[] getConfidenceIntervals(List<Double> data, double confidence) {

        int numberOfObservations = data.size();
        double mean = computeMean(data);
        double stdev = standardDeviation(data, mean);
        Double zscore = getZScoreTable().get(confidence);

        if (zscore == null) {
            System.out.println("Z score not available for provided confidence value: " + confidence);
            return null;
        }

        // X  +-  Z * s / sqrt(n)
        double marginOfError = zscore * stdev / Math.pow(numberOfObservations, 0.5);

        return new double[]{mean + marginOfError, mean - marginOfError};
    }


    public static List<Double> stringsToDoubles(List<String> lst) {
        // return Lists.transform(lst, input -> Double.valueOf(input));
        return lst.stream().map(Double::valueOf).collect(Collectors.toList());
    }


    /**
     * Z-Score Table for Different Confidence Interval Values
     *
     * Taken from http://www.statisticshowto.com/z-alpha2-za2/  &  https://www.mathsisfun.com/data/confidence-interval.html
     * @return
     */
    public static Map<Double, Double> getZScoreTable() {
        Map<Double, Double> zscores = new HashMap<>();
            zscores.put(0.80, 1.282);
            zscores.put(0.85, 1.440);
            zscores.put(0.90, 1.645);
            zscores.put(0.92, 1.750);
            zscores.put(0.95, 1.960);
            zscores.put(0.96, 2.050);
            zscores.put(0.98, 2.326);
            zscores.put(0.99, 2.576);
            zscores.put(0.995, 2.807);
            zscores.put(0.999, 3.291);
        return zscores;
    }


    private static File[] getFilesInDirectory(String directory) {

        File dir = new File(directory);
        if (! (dir.exists() && dir.isDirectory())) {
            return null;
        }

        return dir.listFiles();
    }

    /**
     * Gives one data point for each file of data, which is a Double[] with the mean and confidence intervals
     * @param files
     * @return
     */
    private static List<double[]> reduceToDataPoints(File[] files, double confidence) throws IOException {

        List<double[]> scores = new ArrayList<>(files.length);

        for (File f : files) {

            List<Double> data = stringsToDoubles(
                    readLinesFromFile(f)
            );

            double mean = computeMean(data);
            double stdev = standardDeviation(data, mean);
            double[] confInts = getConfidenceIntervals(data, confidence);

            if (confInts == null) {
                System.out.println("Could not calculate confidence intervals for file: " + f.toString());
                continue;
            }

            scores.add(new double[]{
                    mean, stdev, confInts[0], confInts[1]
            });
        }

        return scores;
    }


    public static void main(String[] args) throws IOException  {


        // TODO   plot this as:   for any given frame, detector has an XYZ% probability of finding 100% of the tadpoles, with (0.95) confidence intervals

//        List<Double> data = stringsToDoubles(readLinesFromFile("/home/ah2166/Videos/tad_test_vids/data_line_separated/1_tad_1.dat"));



        File[] files = getFilesInDirectory("/home/alex/Documents/coding/java/Sproj/src/main/resources/data/raw/evaluationData_1t");


        if (files == null) {
            System.out.println("No files in directory");
            return;
        }

        Arrays.sort(files);

        List<double[]> dataPoints = reduceToDataPoints(files, 0.95);


        System.out.println("\t\tFile\tMean\tStddev\tConf Intervals");


        List<Double> meanSum = new ArrayList<>(dataPoints.size());
        double stdvSum = 0.0;
        double[] cnfIntSum = {0.0, 0.0};

        for (double[] point : dataPoints) {
            System.out.println(String.format(
                    "%s\t%.3f\t%.3f\t[%.3f, %.3f]", files[dataPoints.indexOf(point)].getName(),
                    point[0], point[1], point[2], point[3]
            ));

            meanSum.add(point[0]);
            stdvSum += point[1];
            cnfIntSum[0] += point[2];
            cnfIntSum[1] += point[3];
        }

        System.out.println("----------------------------------------------");


        stdvSum /= dataPoints.size();
        cnfIntSum[0] /= dataPoints.size();
        cnfIntSum[1] /= dataPoints.size();

        System.out.println(String.format(
                "\t\tTotal\t%.3f\t%.3f\t[%.3f, %.3f]",
                computeMean(meanSum), stdvSum, cnfIntSum[0], cnfIntSum[1]
        ));


        System.out.println("\n\n");


        System.out.println(String.format("Mean: %.5f  StDev: %.5f ", computeMean(meanSum), standardDeviation(meanSum, null))
        );

        System.out.println(String.format("Confidence Intervals for all data: %s", Arrays.toString(
                getConfidenceIntervals(meanSum, 0.95)))
        );

        /*

        List<Double> data = stringsToDoubles(
                readLinesFromFile(new File("/home/alex/Documents/coding/java/Sproj/src/main/resources/data/raw/evaluationData_1t/1tadpole_1.dat"))
        );

        System.out.println(String.format("Mean: %.5f  StDev: %.5f ", computeMean(data), standardDeviation(data, null))
        );

        System.out.println(String.format("Confidence Intervals for given data: %s", Arrays.toString(
                getConfidenceIntervals(data, 0.95))
        ));
        */
    }
}