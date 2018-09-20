package sproj.analysis;

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
    public static double[] confidenceIntervals(List<Double> data, double confidence) {

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


    public static void main(String[] args) throws IOException  {


        // TODO   plot this as:   for any given frame, detector has an XYZ% probability of finding 100% of the tadpoles, with (0.95) confidence intervals

        List<Double> data = stringsToDoubles(
                readLinesFromFile("/home/ah2166/Videos/tad_test_vids/data_line_separated/1_tad_1.dat")
        );

        System.out.println(String.format("Mean: %.5f  StDev: %.5f ", computeMean(data), standardDeviation(data, null))
        );

        System.out.println(String.format("Confidence Intervals for given data: %s", Arrays.toString(
                confidenceIntervals(data, 0.95))
        ));
    }

}
