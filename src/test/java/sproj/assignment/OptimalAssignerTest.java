package sproj.assignment;

import org.junit.jupiter.api.Test;
import sproj.tracking.AnimalWithFilter;
import sproj.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimalAssignerTest {

    private OptimalAssigner assigner = new OptimalAssigner();

    @Test
    void testOptimalAssignment() {

//        testSimpleOptimalAssignment();

        for (int i=0; i<10; i++) {
            System.out.println("Testing with " + i + " animals");
            testOptimalAssignmentWhenEqual(i);
        }

    }


    @Test
    void testSimpleOptimalAssignment() {

        int numberOfObjects = 3;

        List<AnimalWithFilter> animals = new ArrayList<>(numberOfObjects);
        animals.add(new AnimalWithFilter(10, 10, null, null, null));
        animals.add(new AnimalWithFilter(50, 50, null, null, null));
        animals.add(new AnimalWithFilter(100, 100, null, null, null));

        List<BoundingBox> boxes = new ArrayList<>(numberOfObjects);
        boxes.add(new BoundingBox(new int[]{105,105}, 5, 5));
        boxes.add(new BoundingBox(new int[]{45,45}, 5, 5));
        boxes.add(new BoundingBox(new int[]{15,15}, 5, 5));


        List<OptimalAssigner.Assignment> expectedAssignments = new ArrayList<>();
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(0), boxes.get(2)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(1), boxes.get(1)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(2), boxes.get(0)));

        List<OptimalAssigner.Assignment> actualAssignments = assigner.getOptimalAssignments(animals, boxes);

        for (int i=0; i<3; i++) {
            assertEquals(expectedAssignments.get(i).animal, actualAssignments.get(i).animal);
            assertEquals(expectedAssignments.get(i).box, actualAssignments.get(i).box);
        }
    }

    private void testOptimalAssignmentWhenEqual(int numberOfObjects) {


        List<AnimalWithFilter> animals = new ArrayList<>(numberOfObjects);

        for (int i=0; i<numberOfObjects; i++) {
            animals.add(new AnimalWithFilter(10, 10, null, null, null));
        }


        animals.add(new AnimalWithFilter(10, 10, null, null, null));
        animals.add(new AnimalWithFilter(50, 50, null, null, null));
        animals.add(new AnimalWithFilter(100, 100, null, null, null));

        List<BoundingBox> boxes = new ArrayList<>(numberOfObjects);
        boxes.add(new BoundingBox(new int[]{105,105}, 5, 5));
        boxes.add(new BoundingBox(new int[]{45,45}, 5, 5));
        boxes.add(new BoundingBox(new int[]{15,15}, 5, 5));


        List<OptimalAssigner.Assignment> expectedAssignments = new ArrayList<>();
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(0), boxes.get(2)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(1), boxes.get(1)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(2), boxes.get(0)));

        List<OptimalAssigner.Assignment> actualAssignments = assigner.getOptimalAssignments(animals, boxes);

        for (int i=0; i<3; i++) {
            assertEquals(expectedAssignments.get(i).animal, actualAssignments.get(i).animal);
            assertEquals(expectedAssignments.get(i).box, actualAssignments.get(i).box);
        }

    }


    @Test
    void testSolveMatrix() {

        // this matrix, where C(i,j) = i*j, is a good test for the Munkres algorithm because it uses all the steps to solve it
        double[][] testMatrix = new double[][]{
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