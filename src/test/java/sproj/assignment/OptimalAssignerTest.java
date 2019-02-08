package sproj.assignment;

import org.bytedeco.javacpp.opencv_core;
import org.junit.jupiter.api.Test;
import sproj.tracking.Animal;
import sproj.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that OptimalAsssigner class solves cost matrices correctly
 * both in best case scenarios and also when there are missing values
 */
class OptimalAssignerTest {

    // TODO: update tests to check for null-padding of values

    private OptimalAssigner assigner = new OptimalAssigner();

    /* if an assertion fails, Animal.toString() is called, which calls toString() on animal.color
     therefore color cannot be null or it will raise a NullPointerException */
    private opencv_core.Scalar fake_clr = new opencv_core.Scalar(0);


    @Test
    void testSolveMatrix() {

        // this matrix, where C(i,j) = i*j, is a good test for the Hungarian algorithm
        // because it requires all the steps to find the solution
        double[][] testMatrix = new double[][]{
                {1d, 2d, 3d, 4d},
                {2d, 4d, 6d, 8d},
                {3d, 6d, 9d, 12d},
                {4d, 8d, 12d, 16d},
        };

        int[][] expectedResult = new int[][] {
                {0, 0, 0, 1},
                {0, 0, 1, 0},
                {0, 1, 0, 0},
                {1, 0, 0, 0}
        };

        int[][] actualResult = assigner.munkresSolveMatrix(testMatrix);

        assertTrue(Arrays.deepEquals(expectedResult, actualResult));
    }

    @Test
    void testSolveMissingRowMatrix() {

        double[][] testMatrix;
        int[][] expectedResult;
        int[][] actualResult;

        testMatrix = new double[][]{
                {1d, 2d, 3d, 4d},
                {2d, 4d, 6d, 8d},
                {3d, 6d, 9d, 12d},
        };

        expectedResult = new int[][]{
                {0, 0, 1, 0},
                {0, 1, 0, 0},
                {1, 0, 0, 0},
                {0, 0, 0, 1}
        };

        actualResult = assigner.munkresSolveMatrix(testMatrix);
        assertTrue(Arrays.deepEquals(expectedResult, actualResult));
    }


    @Test
    void testSolveMissingColMatrix() {

        double[][] testMatrix;
        int[][] expectedResult;
        int[][] actualResult;


        testMatrix = new double[][]{
                {2d, 3d, 4d},
                {4d, 6d, 8d},
                {6d, 9d, 12d},
                {8d, 12d, 16d},
        };

        expectedResult = new int[][] {
                {0, 0, 1, 0},
                {0, 1, 0, 0},
                {1, 0, 0, 0},
                {0, 0, 0, 1}
        };

        actualResult = assigner.munkresSolveMatrix(testMatrix);
        assertTrue(Arrays.deepEquals(expectedResult, actualResult));
    }


    @Test
    void testOptimalAssignments() {

        for (int i=0; i<16; i++) {
            testOptimalAssignmentWhenEqual(i);
            System.out.println("Tested with " + i + " animals");
        }
    }


    @Test
    void testThatOptimalAssignmentsAreNull() {

        List<BoundingBox> boxes = new ArrayList<>();
        boxes.add(new BoundingBox(new int[]{105,105}, 1, 1));
        boxes.add(new BoundingBox(new int[]{155,155}, 1, 1));
        boxes.add(new BoundingBox(new int[]{205,205}, 1, 1));
        boxes.add(new BoundingBox(new int[]{300,300}, 1, 1));

        List<Animal> animals = new ArrayList<>();
        animals.add(new Animal(0, 0, null, fake_clr, null));
        animals.add(new Animal(100, 100, null, fake_clr, null));
        animals.add(new Animal(200, 200, null, fake_clr, null));
        animals.add(new Animal(215, 215, null, fake_clr, null));

        List<OptimalAssigner.Assignment> expectedAssignments = new ArrayList<>();

        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(0), null));

        expectedAssignments.add(new OptimalAssigner.Assignment(null, null));

        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(1), boxes.get(0)));

        expectedAssignments.add(new OptimalAssigner.Assignment(null, null));

        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(2), boxes.get(2)));

        expectedAssignments.add(new OptimalAssigner.Assignment(null, null));

        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(3), null));

        expectedAssignments.add(new OptimalAssigner.Assignment(null, null));


        List<OptimalAssigner.Assignment> actualAssignments = assigner.getOptimalAssignments(animals, boxes);

        for (int i=0; i<expectedAssignments.size(); i++) {
            if (expectedAssignments.get(i).animal == null) {continue;}
            assertEquals(expectedAssignments.get(i).animal, actualAssignments.get(i).animal);
            assertEquals(expectedAssignments.get(i).box, actualAssignments.get(i).box);
        }
    }


    @Test
    void testOptimalAssignmentWithMissingAnimals() {

        int numbOfBoxes = 5;
        int numbMissingAnimals = 2;
        List<BoundingBox> boxes = new ArrayList<>(numbOfBoxes);

        boxes.add(new BoundingBox(new int[]{5,5}, 5, 5));
        boxes.add(new BoundingBox(new int[]{55,55}, 5, 5));
        boxes.add(new BoundingBox(new int[]{105,105}, 5, 5));
        boxes.add(new BoundingBox(new int[]{155,155}, 5, 5));
        boxes.add(new BoundingBox(new int[]{205,205}, 5, 5));

        opencv_core.Scalar fake_clr = new opencv_core.Scalar(0);

        List<Animal> animals = new ArrayList<>(numbOfBoxes - numbMissingAnimals);
        animals.add(new Animal(0, 0, null, fake_clr, null));
        animals.add(new Animal(100, 100, null, fake_clr, null));
        animals.add(new Animal(200, 200, null, fake_clr, null));

        List<OptimalAssigner.Assignment> expectedAssignments = new ArrayList<>(numbOfBoxes - numbMissingAnimals);
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(0), boxes.get(0)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(1), boxes.get(2)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(2), boxes.get(4)));


        List<OptimalAssigner.Assignment> actualAssignments = assigner.getOptimalAssignments(animals, boxes);

        for (int i=0; i<expectedAssignments.size(); i++) {
            if (expectedAssignments.get(i) == null) {
                assertNull(actualAssignments.get(i).animal);
            } else {
                assertEquals(expectedAssignments.get(i).animal, actualAssignments.get(i).animal);
                assertEquals(expectedAssignments.get(i).box, actualAssignments.get(i).box);
            }
        }
    }


    @Test
    void testOptimalAssignmentWithMissingBoxes() {

        int numbOfAnimals = 4;
        int numberMissingBoxes = 2;

        List<Animal> animals = new ArrayList<>(numbOfAnimals);
        animals.add(new Animal(0, 0, null, fake_clr, null));
        animals.add(new Animal(50, 50, null, fake_clr, null));
        animals.add(new Animal(100, 100, null, fake_clr, null));
        animals.add(new Animal(150, 150, null, fake_clr, null));

        List<BoundingBox> boxes = new ArrayList<>(numbOfAnimals - numberMissingBoxes);
        boxes.add(new BoundingBox(new int[]{45,45}, 5, 5));
        boxes.add(new BoundingBox(new int[]{105,105}, 5, 5));

        List<OptimalAssigner.Assignment> expectedAssignments = new ArrayList<>();
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(0), null));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(1), boxes.get(0)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(2), boxes.get(1)));
        expectedAssignments.add(new OptimalAssigner.Assignment(animals.get(3), null));

        List<OptimalAssigner.Assignment> actualAssignments = assigner.getOptimalAssignments(
                animals, boxes
        );

        for (int i=0; i<3; i++) {
            assertEquals(expectedAssignments.get(i).animal, actualAssignments.get(i).animal);
            assertEquals(expectedAssignments.get(i).box, actualAssignments.get(i).box);
        }
    }



    private void testOptimalAssignmentWhenEqual(int numberOfObjects) {

        double multiplier = 10.0;


        List<Animal> animals = new ArrayList<>(numberOfObjects);
        List<BoundingBox> boxes = new ArrayList<>(numberOfObjects);
        List<OptimalAssigner.Assignment> expectedAssignments = new ArrayList<>(numberOfObjects);

        for (int i=0; i<numberOfObjects; i++) {

            Animal anml = new Animal(
                    (int) (multiplier * i), (int) (multiplier * i),
                    null, fake_clr, null);

            BoundingBox box = new BoundingBox(new int[]{
                    (int) (multiplier * i + multiplier / 3), (int) (multiplier * i + multiplier / 3)},
                    1, 1);


            animals.add(anml);
            boxes.add(box);
            expectedAssignments.add(new OptimalAssigner.Assignment(anml, box));
        }

        List<OptimalAssigner.Assignment> actualAssignments = assigner.getOptimalAssignments(animals, boxes);

        for (int i=0; i<numberOfObjects; i++) {
            assertEquals(expectedAssignments.get(i).animal, actualAssignments.get(i).animal);
            assertEquals(expectedAssignments.get(i).box, actualAssignments.get(i).box);
        }

    }
}