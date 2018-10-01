package sproj.assignment;

import sproj.tracking.AnimalWithFilter;
import sproj.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OptimalAssigner {

    private final int COVERED = 1;
    private final int STARRED = 1;
    private final int PRIMED = 2;

    private boolean foundOptimalSolution;

    private final double COST_OF_NON_ASSIGNMENT = 10.0;

    private double[][] costMatrix;
    private int[][] maskMatrix;
    private int[] rowCover;
    private int[] colCover;
    private int rows, cols;

    public OptimalAssigner() {

    }


    private class Assignment {

        public AnimalWithFilter animal;
        public BoundingBox box;

        public Assignment(AnimalWithFilter anml, BoundingBox box) {
            this.animal = anml;
            this.box = box;
        }
    }



    private double costOfAssignment(AnimalWithFilter anml, BoundingBox box) {
        return  Math.pow(Math.pow(anml.x - box.centerX, 2) + Math.pow(anml.y - box.centerY, 2), 0.5);
    }


    public List<Assignment> getOptimalAssignments(final List<AnimalWithFilter> animals, final List<BoundingBox> boundingBoxes) {

        this.foundOptimalSolution = false;
//        List<Assignment> assignments = new ArrayList<>(animals.size());

        rows = animals.size();          // these dimensions will always be equal, even if they are not completely filled,
        cols = boundingBoxes.size();    // but I use separate 'rows' and 'cols' variables for clarity and readability
        int dimension = Math.max(rows, cols);

        costMatrix = new double[dimension][dimension];
        maskMatrix = new int[dimension][dimension];
        rowCover = new int[rows];
        colCover = new int[cols];

        // todo: this is not putting the values in the right place?
        for (int i=0; i<rows; i++) {
            for (int j=0; j<cols; j++) {
                costMatrix[i][j] = costOfAssignment(animals.get(i), boundingBoxes.get(j));
            }
        }

//        List<Assignment> assignments = munkresSolve(costMatrix);

//        return munkresSolve(costMatrix);
        return munkresSolve();

//        System.out.println("Matrix: ");
//        for (int l=0; l<costMatrix.length; l++) {
//            System.out.println(Arrays.toString(costMatrix[l]));
//        }
    }



    /**
     * Munkres Assignment Algorithm. Guarantees optimal assignment, with a worst-case runtime complexity of O(n^3)
     * @return
     */
    public List<Assignment> munkresSolve() { //double[][] costMatrix) {

        List<Assignment> assignments = new ArrayList<>();

        /*int rows = costMatrix.length;       // these dimensions will always be equal, even if they are not completely filled,
        int cols = costMatrix[0].length;    // but I use separate 'rows' and 'cols' variables for clarity and readability
        int[][] maskMatrix;
        int[] rowCover = new int[rows];
        int[] colCover = new int[cols];*/

//        costMatrix = reduceBySmallest(costMatrix, rows, cols);
//        maskMatrix = starZeroes(costMatrix, rowCover, colCover);
//        colCover = coverColumns(maskMatrix, rowCover, colCover);
        reduceBySmallest();  //costMatrix, rows, cols);
        starZeroes();  //costMatrix, rowCover, colCover);
        coverColumns(); //maskMatrix, rowCover, colCover);

        if (! this.foundOptimalSolution) {

        }

        return assignments;
    }

    /** Step 1
     *
     * @return
     */
    private void reduceBySmallest() { //double[][] costMatrix, int rows, int cols) {

        double minVal = costMatrix[0][0];

        for (int r=0; r<rows; r++) {

            for (int c=0; c<cols; c++) {
                minVal = Math.min(costMatrix[r][c], minVal);
            }
            // subtract minimum value from all elements in current row
            for (int c=0; c<cols; c++) {
                costMatrix[r][c] -= minVal;
            }
        }
    }

    /**Step 2
     *
     * @return
     */
    private void starZeroes() { //double[][] costMatrix, int[] rowCover, int[] colCover) {

//        int rows = rowCover.length;
//        int cols = colCover.length;

//        int[][] maskMatrix = new int[rows][cols];

        for (int r=0; r<rows; r++) {

            for (int c = 0; c < cols; c++) {

                if (costMatrix[r][c] == 0 && rowCover[r] == 0 && colCover[c] == 0) {
                    maskMatrix[r][c] = STARRED;
                    rowCover[r] = COVERED;
                    colCover[c] = COVERED;
                }
            }
        }

        /* clearing these rowCover and colCover arrays is unnecessary because they are function-local.
        If they are turned into class variables, this is necessary.
        */
        Arrays.fill(rowCover, 0);
        Arrays.fill(colCover, 0);
    }

    /** Step 3
     *
     * note: does not change maskMatrix
     * @return
     */
    private void coverColumns() {

        for (int r=0; r<rows; r++) {

            for (int c = 0; c < cols; c++) {

                if (maskMatrix[r][c] == 1) {
                    colCover[c] = COVERED;
                }
            }
        }

        int starredZeroesCount = 0;
        for (int c = 0; c < cols; c++) {
            if (colCover[c] == COVERED) {
                starredZeroesCount++;
            }
        }

        if (starredZeroesCount >= cols || starredZeroesCount >= rows) {
            this.foundOptimalSolution = true;
        }
    }







    private int[] findaZero() {

        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                if (costMatrix[r][c] == 0 && rowCover[r] == 0 && colCover[c] == 0) {
                    return new int[]{r, c};
                }
            }

        }
        return null;
    }


    private int locateStarInRow(int row) {
        for (int col=0; col<cols; col++) {
            if (maskMatrix[row][col] == STARRED) {
                return col;
            }
        }
        return -1;
    }


    /**
     * Runtime complexity is O(n!) worst case, will always be at least exponential
     * @return
     */
    public List<Assignment> bruteForceSolve(double[][] costMatrix) {
        List<Assignment> assignments = new ArrayList<>(costMatrix.length);
        double currentCost;

        return assignments;
    }
}
