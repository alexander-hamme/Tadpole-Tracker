package sproj.util;

import com.google.common.base.CharMatcher;
import sproj.assignment.OptimalAssigner;
import sproj.prediction.KalmanFilterBuilder;
import sproj.tracking.Animal;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MissingDataHandeler {

    private final boolean DEBUG = true;

    /**
     * Expects data in the form of a list of N lists, each of which is a list of single coordinates,
     * one for each video frame / timestep from the start to the end of the video
     *
     * The rearranged format is also a list of N lists, but now each list represents
     * all the coordinates for a given frame / timestep of the video
     *
     * @param data
     * @return
     */
    public List<List<Double[]>> rearrangeData(List<List<Double[]>> data, int numbAnmls) {

        int numbLists = data.size();
        assert numbLists == numbAnmls;

        List<List<Double[]>> rearranged = new ArrayList<>(numbLists);

        int numbPoints = data.get(0).size();

        for (List<Double[]> lst : data) {
            assert lst.size() == numbPoints;        // all lists should have same number of points
        }

        for (int n=0; n<numbPoints; n++) {  // iterate through all frames/timesteps of video

            List<Double[]> lst = new ArrayList<>(numbLists);

            for (int i = 0; i < numbLists; i++) {   // iterate through the nested lists at each timestep
                lst.add(data.get(i).get(n));
            }
            rearranged.add(lst);
        }

        if (DEBUG) {
            System.out.println("Rearranged data:");
            for (List<Double[]> lst : rearranged) {
                for (Double[] pt : lst) {
                    System.out.print(Arrays.toString(pt));
                    System.out.print("\t");
                }
                System.out.println();
            }
        }

        return rearranged;
    }

    public List<List<Double[]>> fillInMissingData(File file, int numbAnimals) throws IOException {
        List<List<Integer[]>> points = loadLabeledData(file, numbAnimals);
        return fillInMissingData(points, numbAnimals);
    }

    public List<List<Double[]>> fillInMissingData(List<List<Integer[]>> dataPoints, int numbAnimals) {

        List<List<Double[]>> groupedData = groupDataPoints(dataPoints, numbAnimals);

        for (List<Double[]> group : groupedData) {
            extrapolateMissingData(group);
        }

        if (DEBUG) {
            for (List<Double[]> group : groupedData) {
                for (Double[] pt : group) {
                    System.out.print(Arrays.toString(pt));
                    System.out.print("\t");
                }
                System.out.println();
            }
        }

        return groupedData;
    }

    private double mean(double a, double b) {
        return ((a+b) / 2.0);
    }

    /**
     *
     * @param points
     * @param numbToAdd  number of points to extrapolate between each current point
     */
    private List<List<Double[]>> extrapolateExtraPoints(List<List<Double[]>> points, int numbToAdd) {

        for (List<Double[]> lst : points) {



        }

        return null;
    }

    private void extrapolateMissingData(List<Double[]> points) {

        int size = points.size();
        for (int i=1; i<size; i++) {       //  relies on assumption that first point will always be true
            Double[] pt = points.get(i);
            if (pt[2] == 1.0) {   // point is predicted
                if (i < size - 1) {

                    Double[] prevPt = points.get(i - 1);
                    Double[] nextPt = points.get(i + 1);

                    pt[0] = (double) Math.round(mean(prevPt[0], nextPt[0]));
                    pt[1] = (double) Math.round(mean(prevPt[1], nextPt[1]));

                } else {
                    Double[] prevPt = points.get(i - 1);
                    pt[0] = pt[0] + (pt[0]-prevPt[0]);
                    pt[1] = pt[1] + (pt[1]-prevPt[1]);
                }
            }
        }
    }

    private List<List<Double[]>> groupDataPoints(List<List<Integer[]>> dataPoints, int numbAnimals) {

        List<List<Double[]>> finalPoints = new ArrayList<>();

        OptimalAssigner assigner = new OptimalAssigner();
        OptimalAssigner.DEFAULT_COST_OF_NON_ASSIGNMENT = 500;

        KalmanFilterBuilder filterBuilder = new KalmanFilterBuilder();
        List<Animal> fakeAnimals = new ArrayList<>(numbAnimals);

        for (int i = 0; i < numbAnimals; i++) {
            fakeAnimals.add(new Animal(i, i, new int[]{0, 700, 0,700}, null,
                    filterBuilder.getNewKalmanFilter(i, i, 0.0, 0.0)));

            finalPoints.add(new ArrayList<>());
        }


        for (List<Integer[]> points : dataPoints) {

            long timePos = -1L;

            List<BoundingBox> fakeBoxes = new ArrayList<>(points.size() - 1);
            for (Integer[] pt : points) {
                if (pt.length > 1) {
                    fakeBoxes.add(new BoundingBox(new int[]{pt[0], pt[1]}, 1, 1));
                } else {
                    timePos = (long) pt[0];
                }
            }
            List<OptimalAssigner.Assignment> assignments = assigner.getOptimalAssignments(fakeAnimals, fakeBoxes);

            for (OptimalAssigner.Assignment assignment : assignments) {

                if (assignment.animal == null) {
                    if (assignment.box != null) {
                        System.out.println("No assignment for" + assignment.box.toString());
                    }
                    continue;
                }

                if (assignment.box == null) {       // no assignment
                    assignment.animal.predictTrajectory(0.01, timePos);
                } else {
                    assignment.animal.updateLocation(
                            assignment.box.centerX, assignment.box.centerY, 0.01, timePos, false
                    );
                }
            }

        }


        List<Iterator<double[]>> anmlIterators = new ArrayList<>(numbAnimals);

        for (Animal anml : fakeAnimals) {
            anmlIterators.add(anml.getDataPointsIterator());
        }

        for (int i = 0; i < dataPoints.size(); i++) {

            double[] points = null;

            int idx = 0;

            for (Iterator<double[]> iterator : anmlIterators) {

                 if (iterator.hasNext()) {
                    points = iterator.next();
                    //System.out.print("[" + points[1] + ", " + points[2] + ", " + points[3] + "]");
                    //System.out.print("\t");

                    finalPoints.get(idx++).add(new Double[]{
                            points[1], points[2], points[3], points[0]
                    });
                }
            }
            if (points!=null) {
                //System.out.print(points[0]);
            }
            //System.out.println();

        }
        return finalPoints;
    }

    private List<List<Integer[]>> loadLabeledData(File file, int trueNumbAnmls) throws IOException {

        // TODO: 11/20/18 handle missing values using trueNumbAnmls to check, then extrapolating


        List<String> lines = IOUtils.readLinesFromFile(file);
        List<List<Integer[]>> uniquePoints = new ArrayList<>(lines.size());

        // [211, 88],[257, 76],[279, 60],[421, 66],[0]
        for (String l : lines) {

            String[] split = l.split(",");

            List<Integer[]> points = new ArrayList<>(split.length);

            Set<String> temp = new LinkedHashSet<>(Arrays.asList(split));       // remove duplicate elements
            split = temp.toArray(new String[0]);

            for (int i=0; i<split.length; i++) {
                if (i<split.length-1 && split[i].contains("[") && split[i+1].contains("]")) {

                    points.add(new Integer[]{
                            Integer.valueOf(CharMatcher.javaDigit().retainFrom(split[i])),
                            Integer.valueOf(CharMatcher.javaDigit().retainFrom(split[i+1])),
                    });

                    //points.add(split[i] + split[i+1]);
                } else if (split[i].contains("[") && split[i].contains("]")) {
                    points.add(new Integer[]{
                            Integer.valueOf(CharMatcher.javaDigit().retainFrom(split[i]))
                    });
                    //points.add(split[i]);
                }
            }
            uniquePoints.add(points);
        }

        for (List<Integer[]> lst : uniquePoints) {
            for (Integer[] arr : lst) {
                System.out.print(Arrays.toString(arr));
                System.out.print("\t");
            }
            System.out.println();
        }

        return uniquePoints;
    }
}
