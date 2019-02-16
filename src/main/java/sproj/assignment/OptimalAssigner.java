package sproj.assignment;

import sproj.tracking.Animal;
import sproj.util.BoundingBox;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A modified version of the Munkres Hungarian optimal assignment algorithm
 * implementation coded by Robert A. Pilgrim at Murray State University
 * <a href="http://csclab.murraystate.edu/~bob.pilgrim/445/munkres.html">(link)</a>.
 *
 * The Hungarian algorithm guarantees optimal assignment, with a worst-case
 * runtime complexity of O(n^3), and is therefore ideal for real-time applications.
 *
 * See the following resources for detailed explanations of the algorithm:
 *
 * https://brilliant.org/wiki/hungarian-matching/
 * http://www.math.harvard.edu/archive/20_spring_05/handouts/assignment_overheads.pdf
 * http://www.mathcs.emory.edu/~cheung/Courses/323/Syllabus/Assignment/algorithm.html
 * http://www.hungarianalgorithm.com/solve.php
 *
 * Note that my implementation is different from the version in the link above,
 * in that it uses (both predetermined and dynamically calculated) non-assignment
 * cost values to fill in missing cells in the matrix at each time step.
 */
public class OptimalAssigner {

    private final boolean DEBUG = false;

    // see above resources for explanations of how the algorithm
    // "covers" and "stars" columns to solve the cost matrix

    private final int COVERED = 1;
    private final int UNCOVERED = 0;

    private final int STARRED = 1;
    private final int UNSTARRED = 0;

    // some explanations do not mention priming, however this implementation uses it.
    // it serves a similar purpose as starring.
    private final int PRIMED = 2;
    private final int UNPRIMED = 0;

    /*
     * This value is solely used for matrix cells where the BoundingBox instance
     * exists but the corresponding Animal instance is null.
     *
     * The value of 30.0 was determined to be a good threshold through trial and error,
     * as it is high enough to rarely be a minimum value in a row or col,
     * but not high enough that the algorithm considered it to be worse
     * than giving a grossly erroneous assignment across the screen.
     */
    public static double DEFAULT_COST_OF_NON_ASSIGNMENT = 30.0;

    private DecimalFormat df = new DecimalFormat("#.###");

    private boolean foundOptimalSolution;

    private double[][] costMatrix;
    private int[][] maskMatrix;     // todo:     add explanation
    private int[][] pathMatrix;     // todo:     add explanation
    private int[] rowCover;
    private int[] colCover;
    private int rows, cols;

    public OptimalAssigner() {}

    /**
     * Simple class to contain paired Animal-BoundingBox instances
     */
    public static class Assignment {

        public Animal animal;
        public BoundingBox box;

        public Assignment(Animal anml, BoundingBox box) {
            this.animal = anml;
            this.box = box;
        }
    }


    /**
     * The key function for setting cell values in the cost matrix.
     *
     * If both `anml` and `box` are not null, the matrix cell will be set
     * to the euclidean distance between them.
     *
     * Otherwise, a cost of non-assignment value will be returned.
     *
     * If the Animal instance is null, the default non-assignment cost value is used,
     * because the box will most likely be assigned in another cell.
     *
     * however if the Animal instance is not null, but the BoundingBox instance is,
     * the animal's current cost-of-non-assignment value is used, which is determined
     * by how many frames this Animal instance has not received an assignment.
     *
     * The longer an Animal does not receive an assignment, the larger its individual
     * cost-of-non-assignment value gets, which incentivizes the Hungarian algorithm
     * to assign it to a BoundingBox, even if it is outside the "normal"
     * euclidean range of assignment.
     *
     * @param anml Animal instance, may be null
     * @param box BoundingBox instance, may be null
     * @return double
     */
    private double costOfAssignment(Animal anml, BoundingBox box) {
        if (anml == null) { return DEFAULT_COST_OF_NON_ASSIGNMENT; }
        else if (box == null) { return anml.getCurrCost(); }
        // TODO: round to int?  may reduce computation time --> but check effect on assignment accuracy
        return  Math.pow(Math.pow(anml.x - box.centerX, 2) + Math.pow(anml.y - box.centerY, 2), 0.5);
    }


    /**
     * The main function externally called by the SinglePlateTracker class at each time step.
     *
     * The parameters passed in are the list of Animals and the BoundingBoxes generated by
     * the DetectionsParser class, which are converted from the DetectedObject instances
     * returned by YoloModelContainer.runInference().
     *
     * The length of the animals and boundingBoxes may be different at a given time step.
     * However, when adding fake values to make the matrix square, note that
     * the length of `animals` is always the true value.
     *
     * Each real Animal instance and real BoundingBox instance is padded with a null value.
     * This allows much easier handling of short-duration detection errors at runtime.
     *
     * For example, during tracking if one animal is not detected for 5 frames,
     * the Hungarian algorithm can simply give it null assignments for 5 frames
     * instead of erroneously assigning it to another BoundingBox instance.
     *
     * This also handles erroneous extra detections- if a BoundingBox instance appears
     * that does not correspond to a real Animal instance, it is given a null Animal assignment.
     *
     * @param anmls List of animal instances belonging to SinglePlateTracker class
     * @param boxes List of BoundingBoxes generated by the DetectionsParser class
     * @return List of Assignment instances
     */
    public List<Assignment> getOptimalAssignments(final List<Animal> anmls, final List<BoundingBox> boxes) {

        // Note: Animals are on rows, Bounding Boxes are on columns.

        this.foundOptimalSolution = false;

        final List<Animal> animals = new ArrayList<>(anmls.size() * 2);
        final List<BoundingBox> boundingBoxes = new ArrayList<>(boxes.size() * 2);

        // pad every Animal instance with an extra null value.
        for (Animal a : anmls) {
            animals.add(a);
            animals.add(null);
        }

        // pad every BoundingBox instance with extra null value
        for (BoundingBox b : boxes) {
            boundingBoxes.add(b);
            boundingBoxes.add(null);
        }

        int anmlsSize = animals.size();              // the true value of how many animals there are

        int boxesSize = boundingBoxes.size();        // can be <, ==, or > than animals.size()

        // construct square matrix using whichever size is larger as the dimensions
        int dimension = Math.max(anmlsSize, boxesSize);
        rows = cols = dimension;

        costMatrix = new double[dimension][dimension];
        maskMatrix = new int[dimension][dimension];
        pathMatrix = new int[dimension*2 + 1][2];

        rowCover = new int[rows];
        colCover = new int[cols];

        // fill cost matrix with cost values
        for (int i=0; i<anmlsSize; i++) {
            for (int j=0; j<boxesSize; j++) {
                costMatrix[i][j] = Double.parseDouble(df.format(
                        costOfAssignment(animals.get(i), boundingBoxes.get(j))
                ));
            }
        }

        // (no longer used, to be removed soon)
        // fillBlanks(anmlsSize, boxesSize);

        return parseSolvedMatrix(munkresSolve(), animals, boundingBoxes);
    }

    public double[][] getCostMatrix() {
        return costMatrix;
    }

    public int[][] getMaskMatrix() {
        return maskMatrix;
    }

    /**
     * Parse solved cost matrix and create corresponding Assignment instances.
     *
     * @param solvedMatrix  costMatrix generated by solveMatrix()
     * @param animals
     * @param boundingBoxes
     * @return
     */
    private List<Assignment> parseSolvedMatrix(final int[][] solvedMatrix,
               final List<Animal> animals, final List<BoundingBox> boundingBoxes) {

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
                        // do nothing (this should never execute)
                    }
                }
            }
        }
        return assignments;
    }


    /**
     * The actual implementation of the Munkres Assignment Algorithm.
     *
     * Guarantees optimal assignment, with a worst-case runtime complexity of O(n^3)
     *
     * @return int[][], the solved cost matrix
     */
    public int[][] munkresSolve() {

            if (DEBUG) {printUpdate(0);}

        // Step 1
        reduceBySmallest();

            if (DEBUG) {printUpdate(1);}

        // Step 2
        starZeroes();

            if (DEBUG) {printUpdate(2);}

        int nextStep = 3;

        while(true) {

            if (DEBUG) {printUpdate(nextStep);}

            if (nextStep == 7) {
                break;
            }

            switch (nextStep) {

                case 3:  // goes to either step 4 or 7 (the end)

                    coverColumns();

                    if (this.foundOptimalSolution) {
                        nextStep = 7;
                    } else {
                        nextStep = 4;
                    }
                    break;

                case 4: // goes to either step 3 or 6

                    int[] position = primeZeros();

                    if (position == null) {
                        nextStep = 6;

                    } else {               // Step 5 happens right after 4
                        augmentingPathAlgorithm(position[0], position[1]);
                        nextStep = 3;
                    }
                    break;

                case 6:  // goes back to step 4
                    applySmallestVal();
                    nextStep = 4;
                    break;
            }
        }


        if (DEBUG) {

            System.out.println("Solved. Final Matrix:");

            for (double[] subArr : costMatrix) {
                System.out.println(Arrays.toString(subArr));
            }
            for (int[] subArr : maskMatrix) {
                System.out.println(Arrays.toString(subArr));
            }
        }

        return maskMatrix;
    }

    /**
     * Step 1
     *
     * Finds the smallest value in each row and subtracts it from
     * all the cells in that row
     *
     * Changes: costMatrix
     */
    private void reduceBySmallest() {

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

    /**
     * Step 2
     *
     * todo add explanation
     *
     * Changes: maskMatrix, rowCover, and colCover
     */
    private void starZeroes() {

        for (int r=0; r<rows; r++) {

            for (int c = 0; c < cols; c++) {

                if (costMatrix[r][c] == 0 && rowCover[r] == 0 && colCover[c] == 0) {
                    maskMatrix[r][c] = STARRED;
                    rowCover[r] = COVERED;
                    colCover[c] = COVERED;
                }
            }
        }
        Arrays.fill(rowCover, 0);
        Arrays.fill(colCover, 0);
    }

    /**
     * Step 3
     *
     * todo add explanation
     *
     * Changes: rowCover and colCover
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


    /**
     * Step four
     *
     * Primes the cells in the matrix that contain zeros
     *
     * Changes: maskMatrix, rowCover, and colCover
     *
     * @return
     */
    private int[] primeZeros() {

        int row;
        int col;

        while (true) {

            int[] position = findaZero();

            if (position == null) {
                // goes to step 6
                return null;

            } else {

                // todo add explanation

                row = position[0];
                col = position[1];

                maskMatrix[row][col] = PRIMED;

                int newCol = locateStarInRow(row);

                if (newCol != -1) {

                    rowCover[row] = COVERED;
                    colCover[newCol] = UNCOVERED;

                } else {
                    // goes to step 5
                    return new int[]{row, col};
                }
            }
        }
    }


    /**
     * Step Five
     *
     * Related to the augmenting path algorithm
     * (see https://theory.stanford.edu/~tim/w16/l/l2.pdf)
     *
     * todo add more explanation
     *
     * Returns to step 3
     *
     * Changes: pathMatrix, maskMatrix (in flipPathValues() and erasePrimes()),
     *          rowCover and colCover (in clearCoverMatrices())
     *
     * @param pathRow
     * @param pathCol
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

            // todo     add explanation

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

    /**
     * Step 6
     *
     * For each covered row, add the smallest value to each element.
     * For each uncovered column, subtract that value from each element.
     *
     * Finally, go back to Step 4 without changing any of the stars, primes, or covers
     *
     * Changes: costMatrix
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


    /*
     * The following functions are utility functions used by the algorithm steps above
     */


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
            maskMatrix [pathMatrix[p][0]] [pathMatrix[p][1]] =
                    (val == STARRED) ? UNSTARRED : STARRED;

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

                if (costMatrix[r][c] == UNSTARRED
                    && rowCover[r] == UNCOVERED
                    && colCover[c] == UNCOVERED) {
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



    /* Functions only used for testing and debugging purposes */

    private void printUpdate(int nextStep) {
        System.out.println(String.format(
                "\n--------Step %d--------\n" +
                        "Cost Matrix: \n%s\n" +
                        "Mask Matrix: \n%s\n" +
                        "Row Cover: %s\n" +
                        "Col Cover: %s\n" +
                "---------------------------------\n",
                nextStep, matrix2dToString(costMatrix), matrix2dToString(maskMatrix),
                Arrays.toString(rowCover), Arrays.toString(colCover)
        ));
    }

    public String matrix2dToString(int[][] matrix) {
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

    /**
     * Used only in the OptimalAssignerTest JUnit Test
     *
     * Takes cost matrix as a parameter instead of using the class costMatrix attribute
     * @param matrix
     * @return
     */
    public int[][] munkresSolveMatrix(double[][] matrix) {

        int dimension = Math.max(matrix.length, matrix[0].length);
        rows = cols = dimension;

        costMatrix = new double[dimension][dimension];
        maskMatrix = new int[dimension][dimension];
        pathMatrix = new int[dimension*2 + 1][2];

        rowCover = new int[rows];
        colCover = new int[cols];

        for (int r=0; r<matrix.length; r++) {
            if (matrix[r].length >= 0) System.arraycopy(
                    matrix[r], 0, costMatrix[r], 0, matrix[r].length
            );
        }

        return munkresSolve();
    }


    /**
     * This function is no longer used.
     *
     * Previously filled the extra cells in the matrix with
     * DEFAULT_COST_OF_NON_ASSIGNMENT.
     *
     * Will be removed in the near future.
     *
     * @param rows
     * @param cols
     */
    private void fillBlanks(int rows, int cols) {

        if (cols < rows) {  // rows = animals.size,   cols = boundingboxes.size
            for (int r=0; r<rows; r++) {
                for (int c = cols; c < rows; c++) {
                    costMatrix[r][c] = DEFAULT_COST_OF_NON_ASSIGNMENT;
                }
            }
        } else if (rows < cols) {
            for (int r = rows; r < cols; r++) {
                for (int c = 0; c < cols; c++) {
                    costMatrix[r][c] = DEFAULT_COST_OF_NON_ASSIGNMENT;
                }
            }
        }
    }
}