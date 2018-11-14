package sproj.assignment;

import sproj.tracking.Animal;
import sproj.util.BoundingBox;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OptimalAssigner {


    /* TODO  make sure there are never ZEROS in the starting cost matrix */

    private final boolean DEBUG = false;

    private final int COVERED = 1;
    private final int UNCOVERED = 0;
    private final int STARRED = 1;
    private final int UNSTARRED = 0;
    private final int PRIMED = 2;
    private final int UNPRIMED = 0;

    public static final double DEFAULT_COST_OF_NON_ASSIGNMENT = 30.0;     // this should be high enough to not be a minimum value in a row or col,
                                                            // but not high enough that it's worse than giving an assignment a value across the screen
                                                            // TODO:       find out the highest (true) distance that tadpoles can cover in a frame or two and
                                                            // todo        multiply  this by number of frames skipped

    private DecimalFormat df = new DecimalFormat("#.###");
    private boolean foundOptimalSolution;
    private double[][] costMatrix;
    private int[][] maskMatrix;
    private int[][] pathMatrix;
    private int[] rowCover;
    private int[] colCover;
    private int rows, cols;


    public OptimalAssigner() {

    }


    public static class Assignment {

        public Animal animal;
        public BoundingBox box;

        public Assignment(Animal anml, BoundingBox box) {
            this.animal = anml;
            this.box = box;
        }
    }


    /**
     * The length of the animals and boundingBoxes may be different at certain points in time.
     * However, when adding fake values to make the matrix square, it should be kept in mind that
     * the length of `animals` is always the true value.
     *
     * @param anmls
     * @param boxes
     * @return
     */
    public List<Assignment> getOptimalAssignments(final List<Animal> anmls, final List<BoundingBox> boxes) {

        // Note: Animals are on rows, Bounding Boxes are on columns.

        this.foundOptimalSolution = false;

        final List<Animal> animals = new ArrayList<>(anmls.size() * 2);
        final List<BoundingBox> boundingBoxes = new ArrayList<>(boxes.size() * 2);

        for (Animal a : anmls) {
            animals.add(a);
            animals.add(null);
        }

        for (BoundingBox b : boxes) {
            boundingBoxes.add(b);
            boundingBoxes.add(null);
        }

//        List<Assignment> assignments = new ArrayList<>(animals.size());

        int anmlsSize = animals.size();              // the true value of how many animals there are
        int boxesSize = boundingBoxes.size();        // will usually be less than or equal to (only in rare cases more than) animals.size()

        int dimension = Math.max(anmlsSize, boxesSize);
        rows = cols = dimension;

        costMatrix = new double[dimension][dimension];
        maskMatrix = new int[dimension][dimension];
        pathMatrix = new int[dimension*2 + 1][2];           // todo:     explain

        rowCover = new int[rows];
        colCover = new int[cols];

        for (int i=0; i<anmlsSize; i++) {
            for (int j=0; j<boxesSize; j++) {
                costMatrix[i][j] = Double.parseDouble(df.format(costOfAssignment(animals.get(i), boundingBoxes.get(j))));
            }
        }

        fillBlanks(anmlsSize, boxesSize);

        int[][] solvedMatrix = munkresSolve();

        // reset costmatrix, maskMatrix, etc by setting to null?

        return parseSolvedMatrix(solvedMatrix, animals, boundingBoxes);
    }

    public double[][] getCostMatrix() {
        return costMatrix;
    }


    public int[][] getMaskMatrix() {
        return maskMatrix;
    }

    private double costOfAssignment(Animal anml, BoundingBox box) {
        if (anml == null) { return DEFAULT_COST_OF_NON_ASSIGNMENT; }
        else if (box == null) { return anml.getCurrNonAssignmentCost(); }
        return  Math.pow(Math.pow(anml.x - box.centerX, 2) + Math.pow(anml.y - box.centerY, 2), 0.5);
    }


    private List<Assignment> parseSolvedMatrix(final int[][] solvedMatrix, final List<Animal> animals,
                                               final List<BoundingBox> boundingBoxes) {

        ArrayList<Assignment> assignments = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (solvedMatrix[r][c] == STARRED) {

                    if (r < animals.size()) {

                        if (c < boundingBoxes.size()) {
                            assignments.add(new Assignment(animals.get(r), boundingBoxes.get(c)));
                        } else {
                            assignments.add(new Assignment(animals.get(r), null));
                        }

                    } else {
                        // todo:   do anything here?     -->  this means extra (false) bounding box detections
                    }
                }
            }
        }

        //        if (assignments.size() < animals.size()) {

        // TODO get rid of this    (it shouldn't ever be called?
        for (Animal anml : animals) {

            if (anml == null) {continue;}

            boolean alreadyAssigned = false;

            for (Assignment assignment : assignments) {

                if (anml.equals(assignment.animal)) {
                    alreadyAssigned = true;
                }
            }
            if (!alreadyAssigned) {
                assignments.add(new Assignment(anml, null));    // no box assignment for this animal
            }
        }


        //TODO remove all double null assignments from list?

        return assignments;
    }












    /**
     * The length of the animals and boundingBoxes may be different at certain points in time.
     * However, when adding fake values to make the matrix square, it should be kept in mind that
     * the length of `animals` is always the true value.
     *
     * @param animals
     * @param boundingBoxes
     * @return
     */
    public List<Assignment> getOptimalAssignmentsWithoutExtraNulls(
            final List<Animal> animals, final List<BoundingBox> boundingBoxes) {

        // Note: Animals are on rows, Bounding Boxes are on columns.

        this.foundOptimalSolution = false;
//        List<Assignment> assignments = new ArrayList<>(animals.size());

        int anmlsSize = animals.size();              // the true value of how many animals there are
        int boxesSize = boundingBoxes.size();        // will usually be less than or equal to (only in rare cases more than) animals.size()


        /*
        rows = anmlsSize;
        cols = boxesSize;

        int dimension = Math.max(rows, cols); */


        int dimension = Math.max(anmlsSize, boxesSize);
        rows = cols = dimension;

        // TODO     it seems that the assignment algorithm still finds optimal assignments even if the passed in matrices
        // todo     have unbalanced dimensions... double check that this is always true


        costMatrix = new double[dimension][dimension];
        maskMatrix = new int[dimension][dimension];
        pathMatrix = new int[dimension*2 + 1][2];           // todo:     explain

        rowCover = new int[rows];
        colCover = new int[cols];

        for (int i=0; i<anmlsSize; i++) {
            for (int j=0; j<boxesSize; j++) {
                costMatrix[i][j] = Double.parseDouble(df.format(costOfAssignment(animals.get(i), boundingBoxes.get(j))));
            }
        }

        fillBlanks(anmlsSize, boxesSize);          // TODO     check if this does what you want

        int[][] solvedMatrix = munkresSolve();


        // reset costmatrix, maskMatrix, etc by setting to null?

        return parseSolvedMatrix(solvedMatrix, animals, boundingBoxes);
    }


    private void fillBlanks(int rws, int cls) {

        if (cls < rws) {                      // rows = animals.size,   cols = boundingboxes.size
            for (int r=0; r<rws; r++) {
                for (int c = cls; c < rws; c++) {
                    costMatrix[r][c] = DEFAULT_COST_OF_NON_ASSIGNMENT;
                }
            }
        } else if (rws < cls) {
            for (int r = rws; r < cls; r++) {
                for (int c = 0; c < cls; c++) {
                    costMatrix[r][c] = DEFAULT_COST_OF_NON_ASSIGNMENT;
                }
            }
        }
    }


    /**
     * Munkres Assignment Algorithm. Guarantees optimal assignment, with a worst-case runtime complexity of O(n^3)
     * @return
     */
    public int[][] munkresSolve() { //double[][] costMatrix) {

        List<Assignment> assignments = new ArrayList<>();

        /*int rows = costMatrix.length;       // these dimensions will always be equal, even if they are not completely filled,
        int cols = costMatrix[0].length;    // but I use separate 'rows' and 'cols' variables for clarity and readability
        int[][] maskMatrix;
        int[] rowCover = new int[rows];
        int[] colCover = new int[cols];

        costMatrix = reduceBySmallest(costMatrix, rows, cols);
        maskMatrix = starZeroes(costMatrix, rowCover, colCover);
        colCover = coverColumns(maskMatrix, rowCover, colCover);*/

            if (DEBUG) {printUpdate(0);}

        reduceBySmallest();         // step 1               //costMatrix, rows, cols);

            if (DEBUG) {printUpdate(1);}

        starZeroes();               // step 2               //costMatrix, rowCover, colCover);

            if (DEBUG) {printUpdate(2);}


        int nextStep = 3;

        while(true) {

            if (DEBUG) {printUpdate(nextStep);}

            if (nextStep == 7) {
                break;
            }

            switch (nextStep) {


                case 3:

                    coverColumns();             // Step 3

                    if (this.foundOptimalSolution) {
                        nextStep = 7;
                    } else {
                        nextStep = 4;
                    }
                    break;

                case 4:

                    int[] position = primeZeros();    // Step 4

                    if (position == null) {
                        nextStep = 6;

                    } else {                          // Step 5 happens right after 4
                        augmentingPathAlgorithm(position[0], position[1]);
                        nextStep = 3;
                    }
                    break;

                case 6:
                    applySmallestVal();
                    nextStep = 4;
                    break;
            }
        }


        if (DEBUG) {

            System.out.println("Solved. Final Matrix:");

            for (double[] aCostMatrix : costMatrix) {
                System.out.println(Arrays.toString(aCostMatrix));
            }
            for (int[] aMaskMatrix : maskMatrix) {
                System.out.println(Arrays.toString(aMaskMatrix));
            }
        }

        // step 7

//        return costMatrix;
        return maskMatrix;
    }

    /** Step 1
     *
     * @return
     */
    private void reduceBySmallest() { //double[][] costMatrix, int rows, int cols) {

        double minVal;

        for (int r=0; r<rows; r++) {

            minVal = costMatrix[r][0];

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


    /** Step four
     *
     * @return
     */
    private int[] primeZeros() {

        int row;
        int col;

        while (true) {

            int[] position = findaZero();

            if (position == null) { // go to step 6
                return null;

            } else {

                row = position[0];
                col = position[1];

                maskMatrix[row][col] = PRIMED;

                int newCol = locateStarInRow(row);

                if (newCol != -1) {

                    rowCover[row] = COVERED;
                    colCover[newCol] = UNCOVERED;

                } else {  // go to step 5
                    return new int[]{row, col};
                }
            }
        }
    }


    /** Step Five
     *
     * Related to the augmenting path algorithm
     *
     * Returns to step 3
     * @return
     */
    private void augmentingPathAlgorithm(int pathRow, int pathCol) {

        int pathCount = 1;
        pathMatrix[0][0] = pathRow;
        pathMatrix[0][1] = pathCol;

        while (true) {

            int row = locateStarInCol(pathMatrix[pathCount-1][1]);

            if (row == -1) {
                break;
            }

            pathCount++;
            pathMatrix[pathCount-1][0] = row;
            pathMatrix[pathCount-1][1] = pathMatrix[pathCount-2][1];

            int col = locatePrimeInRow(pathMatrix[pathCount-1][0]);
            pathCount ++;
            pathMatrix[pathCount-1][0] = pathMatrix[pathCount-2][0];
            pathMatrix[pathCount-1][1] = col;
        }

        flipPathValues(pathCount);
        clearCoverMatrices();
        erasePrimes();
    }

    /**Step 6
     *
     *
     * For each covered row, add the smallest value to each element.
     * For each uncovered column, subtract that value from each element.
     *
     * Finally, go back to Step 4 without changing any of the stars, primes, or covers
     */
    private void applySmallestVal() {

        double minVal = findSmallestValue();

        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                if (rowCover[r] == COVERED)
                    costMatrix[r][ c] += minVal;

                if (colCover[c] == UNCOVERED)
                    costMatrix[r][c] -= minVal;
            }
        }
    }
    private double findSmallestValue() {

        double minVal = Double.MAX_VALUE;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (rowCover[r] == UNCOVERED && colCover[c] == UNCOVERED) {
                    minVal = Math.min(minVal, costMatrix[r][c]);
                }
            }
        }
        return minVal;
    }

    private void clearCoverMatrices() {
        Arrays.fill(rowCover, UNCOVERED);
        Arrays.fill(colCover, UNCOVERED);
    }


    private void flipPathValues(int pathCount) {
        for (int p=0; p<pathCount; p++) {
            int val = maskMatrix [pathMatrix[p][0]]  [pathMatrix[p][1]];
            // flip all Starred values to Unstarred, and vice versa
            maskMatrix [pathMatrix[p][0]]  [pathMatrix[p][1]] = (val == STARRED) ? UNSTARRED : STARRED;

        }
    }

    private void erasePrimes() {

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (maskMatrix[r][c] == PRIMED) {
                    maskMatrix[r][c] = UNPRIMED;
                }
            }
        }
    }

    private int[] findaZero() {

        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                if (costMatrix[r][c] == UNSTARRED && rowCover[r] == UNCOVERED && colCover[c] == UNCOVERED) {
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

    private int locateStarInCol(int col) {
        for (int row=0; row<cols; row++) {
            if (maskMatrix[row][col] == STARRED) {
                return row;
            }
        }
        return -1;
    }

    private int locatePrimeInRow(int row) {
        for (int col=0; col<cols; col++) {
            if (maskMatrix[row][col] == PRIMED) {
                return col;
            }
        }
        return -1;
    }









    /* Functions for testing purposes */

    private void printUpdate(int nextStep) {
        System.out.println(String.format(
                "\n--------Step %d--------\n" +
                        "Cost Matrix: \n%s\n" +
                        "Mask Matrix: \n%s\n" +
                        "Row Cover: %s\n" +
                        "Col Cover: %s\n" +
                "---------------------------------\n",
                nextStep, matrix2dToString(costMatrix), matrix2dIntToString(maskMatrix),
                Arrays.toString(rowCover), Arrays.toString(colCover)
        ));
    }

    public static String matrix2dIntToString(int[][] matrix) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int[] aMatrix : matrix) {
            stringBuilder.append(Arrays.toString(aMatrix)).append("\n");
        }
        return stringBuilder.toString();
    }

    private String matrix2dToString(double[][] matrix) {
        StringBuilder stringBuilder = new StringBuilder();
        for (double[] aMatrix : matrix) {
            stringBuilder.append(Arrays.toString(aMatrix)).append("\n");
        }
        return stringBuilder.toString();
    }

    public int[][] munkresSolveMatrix(double[][] matrix) {

        int dimension = Math.max(matrix.length, matrix[0].length);
        rows = cols = dimension;

        costMatrix = new double[dimension][dimension];
        maskMatrix = new int[dimension][dimension];
        pathMatrix = new int[dimension*2 + 1][2];

        rowCover = new int[rows];
        colCover = new int[cols];

        for (int r=0; r<matrix.length; r++) {
            if (matrix[r].length >= 0) System.arraycopy(matrix[r], 0, costMatrix[r], 0, matrix[r].length);
        }


        // TODO     check if this does what you want
        fillBlanks(matrix.length, matrix[0].length);

        // Put fake values in the cost matrix to make up for missing data

        /*if (cols < rows) {                      // rows = animals.size,   cols = boundingboxes.size
            for (int r=0; r<rows; r++) {
                for (int c = cols; c < rows; c++) {
                    costMatrix[r][c] = FAKE_VALUE;
                }
            }
        } else if (rows < cols) {
            for (int r=rows; r<cols; r++) {
                for (int c = 0; c < cols; c++) {
                    costMatrix[r][c] = FAKE_VALUE;
                }
            }
        }*/


        return munkresSolve();
    }







    private List<Assignment> parseSolvedMatrixORIGINAL(final int[][] solvedMatrix, final List<Animal> animals,
                                                       final List<BoundingBox> boundingBoxes) {

        ArrayList<Assignment> assignments = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (solvedMatrix[r][c] == STARRED) {

                    // handle missing assignments

                    if (r < animals.size()) {

                        if (c < boundingBoxes.size()) {
                            assignments.add(new Assignment(animals.get(r), boundingBoxes.get(c)));
                        } else {
                            assignments.add(new Assignment(animals.get(r), null));
                        }

                    } else {
                        // todo:   do anything here?     -->  this means extra (false) bounding box detections
                    }
                }
            }
        }

        //        if (assignments.size() < animals.size()) {

        for (Animal anml : animals) {

            for (Assignment assignment : assignments) {

                if (anml.equals(assignment.animal)) {
                    break;
                }
            }
            // TODO  make sure this only happens if break doesn't get called in the second for loop!!
            assignments.add(new Assignment(anml, null));    // no box assignment for this animal
        }

        return assignments;
    }




    public static void main(String[] args) {

        OptimalAssigner assigner = new OptimalAssigner();

        double[][] testMatrix = new double[][]{
                {4d, 3d, 2d, 1d}, //{1d, 2d, 3d},
                {8d, 6d, 4d, 2d},
                {12d, 9d, 6d, 3d}
        };

        assigner.munkresSolveMatrix(testMatrix);
    }


    /**
     * Runtime complexity is O(n!) worst case, will always be at least exponential
     * @return
     *//*
    public List<Assignment> bruteForceSolve(double[][] costMatrix) {
        List<Assignment> assignments = new ArrayList<>(costMatrix.length);
        double currentCost;

        return assignments;
    }*/
}
