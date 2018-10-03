package sproj.assignment;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class OptimalAssignerTest {


    @Test
    void munkresSolve() {
    }

    @Test
    void testSolveMatrix() {

        OptimalAssigner assigner = new OptimalAssigner();

        double[][] testMatrix = new double[][]{     // this matrix is a worst-case test for the Munkres algorithm
                {1d, 2d, 3d},
                {2d, 4d, 6d},
                {3d, 6d, 9d}
        };

        int[][] expectedResult = new int[][] {
                {0, 0, 1},
                {0, 1, 0},
                {1, 0, 0}
        };

        int[][] actualResult = assigner.munkresSolveMatrix(testMatrix);

        assertTrue(Arrays.deepEquals(expectedResult, actualResult));
    }
}