package sproj.assignment;

import sproj.tracking.AnimalWithFilter;
import sproj.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OptimalAssigner {

    private final double COST_OF_NON_ASSIGNMENT = 10.0;

    private class Assignment {

        public AnimalWithFilter animal;
        public BoundingBox box;

        public Assignment(AnimalWithFilter anml, BoundingBox box) {
            this.animal = anml;
            this.box = box;
        }
    }

    /**
     * Runtime complexity is O(n!) worst case, will always be at least exponential
     * @return
     */
    public List<Assignment> bruteForceSolve(List<AnimalWithFilter> animals, List<BoundingBox> boundingBoxes) {
        List<Assignment> assignments = new ArrayList<>(animals.size());
        double currentCost;

        return assignments;
    }

    /**
     * Munkres Assignment Algorithm. Guarantees optimal assignment, with a worst-case runtime complexity of O(n^3)
     * @return
     */
    public List<Assignment> munkresSolve(double[][] costMatrix) {
        List<Assignment> assignments = new ArrayList<>();
        double currentCost;

        return assignments;
    }

    private double costOfAssignment(AnimalWithFilter anml, BoundingBox box) {
        return  Math.pow(Math.pow(anml.x - box.centerX, 2) + Math.pow(anml.y - box.centerY, 2), 0.5);
    }


    public List<Assignment> getOptimalAssignments(final List<AnimalWithFilter> animals, final List<BoundingBox> boundingBoxes) {

        List<Assignment> assignments = new ArrayList<>(animals.size());

        int rows = animals.size();
        int cols = boundingBoxes.size();
        int dimension = Math.max(rows, cols);

        double[][] costMatrix = new double[dimension][dimension];

        for (int i=0; i<rows; i++) {
            for (int j=0; j<cols; j++) {
                costMatrix[i][j] = costOfAssignment(animals.get(i), boundingBoxes.get(j));
            }
        }

        System.out.println(Arrays.toString(costMatrix));

        return assignments;
    }
}
