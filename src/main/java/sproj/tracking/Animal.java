package sproj.tracking;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.math3.filter.KalmanFilter;
import org.bytedeco.javacpp.opencv_core.Scalar;
import sproj.assignment.OptimalAssigner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Class used to record the motion data of individual
 * subject animals during tracking
 */
public class Animal {

    private final boolean DEBUG = false;

    private final double DEFAULT_COST_OF_NON_ASSIGNMENT = OptimalAssigner.DEFAULT_COST_OF_NON_ASSIGNMENT;     // this should be high enough to not be a minimum value in a row or col,

    public Scalar color;
    public final int LINE_THICKNESS = 2;
    public final int CIRCLE_RADIUS = 15;
    public final int LINE_POINTS_SIZE = 64;

    public static final int DATA_BUFFER_ARRAY_SIZE = 60;

    private ArrayList<double[]> dataPoints;
    private CircularFifoQueue<int[]> linePoints;

    public int x, y;
    public double vx, vy;
    public double ax, ay;   // acceleration
    public double currentHeading;

    public KalmanFilter trackingFilter;
    private int[] positionBounds;

    // count of consecutive time steps that have not had true assignment updates
    private int timeStepsPredicted;

    // dynamic cost value that increases if `timeStepsPredicted` increases
    private double currCostNonAssignnmnt;

    private final double MAXCOST = 1000.0;

    private boolean PREDICT_WITH_VELOCITY = true;

    public Animal(int _x, int _y, final int[] positionBounds, final Scalar clr, KalmanFilter kFilter) {

        this.x = _x; this.y = _y;
        this.positionBounds = positionBounds;
        currentHeading = 0;
        color = clr; // new Scalar(clr[0], clr[1], clr[2], 1.0);
        linePoints = new CircularFifoQueue<>(LINE_POINTS_SIZE);
        dataPoints = new ArrayList<>(DATA_BUFFER_ARRAY_SIZE);
        trackingFilter = kFilter;

        this.timeStepsPredicted = 0;
        this.currCostNonAssignnmnt = DEFAULT_COST_OF_NON_ASSIGNMENT;

    }

    @Override
    public String toString() {
        return String.format("animal at [%d,%d] with color %s", this.x, this.y, this.color.toString());
    }

    public void setCurrCostNonAssignnmnt(final double val) {
        currCostNonAssignnmnt = val;
    }

    public double getCurrNonAssignmentCost() {
        return currCostNonAssignnmnt;
    }

    public void clearPoints() {
        this.dataPoints.clear();
    }

    /**
     * Note that the order of parameters to this function does not match
     * the order in which the data points are written to file
     *
     * @param _x
     * @param _y
     * @param dt
     * @param timePos
     * @param isPredicted
     */
    public void updateLocation(int _x, int _y, double dt, long timePos, boolean isPredicted) {

        if (isPredicted) {
            timeStepsPredicted++;
            currCostNonAssignnmnt = (currCostNonAssignnmnt + timeStepsPredicted) % MAXCOST;
            if (DEBUG) {System.out.println("Current cost: " + currCostNonAssignnmnt);}
        } else {
            timeStepsPredicted = 0;
            currCostNonAssignnmnt = DEFAULT_COST_OF_NON_ASSIGNMENT;
        }

        // todo calculate a probability of correctness instead of binary value
        double predicted = isPredicted ? 1 : 0;

        this.x = _x; this.y = _y;
        applyBoundsConstraints();
        dataPoints.add(new double[]{timePos, this.x, this.y, predicted});
        linePoints.add(new int[]{this.x, this.y});   // calls the addFirst() method, adds to front of Deque
        updateVelocity(dt);
        updateKFilter();
    }


    private void applyBoundsConstraints() {
        x = (x>positionBounds[0]) ? x : positionBounds[0];
        x = (x<positionBounds[1]) ? x : positionBounds[1];
        y = (y>positionBounds[2]) ? y : positionBounds[2];
        y = (y<positionBounds[3]) ? y : positionBounds[3];
    }

    public void predictTrajectory(double dt, long timePos) {

        double[] predictedState = getPredictedState();

        if (DEBUG) {
            System.out.println(String.format("Current [(%d,%d)(%.3f,%.3f)], estimation: %s",
                    this.x, this.y, this.vx, this.vy, Arrays.toString(predictedState))
            );
        }

        double predX = predictedState[0]; //(int) Math.round(predictedState[0]);
        double predY = predictedState[1]; //(int) Math.round(predictedState[1]);
        double vx = predictedState[2];
        double vy = predictedState[3];

        int newx, newy;
        if (PREDICT_WITH_VELOCITY) {

            /* TODO if movementstate.stationary: just use predicted x & y */

            double displThresh = 10.0;

            newx = (int) Math.round(this.x + (vx * dt));
            newy = (int) Math.round(this.y - (vy * dt));
            newx = (Math.abs(newx - predX) > displThresh) ? (int) Math.round(predX) : newx;
            newy = (Math.abs(newy - predY) > displThresh) ? (int) Math.round(predY) : newy;

        } else {         // Simplest method
            newx = (int) Math.round(predX);
            newy = (int) Math.round(predY);
        }

        if (DEBUG) {System.out.println(String.format("new coordinates: (%d, %d)", newx, newy));}

        // alternative method:  use predicted position to calculate heading, and then factor in velocity to predict new position

        updateLocation(newx, newy, dt, timePos, true);
    }


    /** Use line points array because it keeps track of the most recent points */
    private void updateVelocity(double dt) {        // TODO use timePos points to calculate dt!

        int subtractionIdx = 3;  // calculate velocity over the last N frames
        if (linePoints.size() < subtractionIdx + 1) {
            this.vx = 0;
            this.vy = 0;

        } else {
            // todo average these out so the change in values isnt so drastic
            int[] prevPoint = linePoints.get(linePoints.size() - 1 - subtractionIdx);   // the most recent point is at the end index
            this.vx = (this.x - prevPoint[0]) / (subtractionIdx * dt);
            /* flip the subtraction because y axis in graphics increases by going down instead of up */
             this.vy = ((prevPoint[1] - this.y) / (subtractionIdx * dt));
        }
    }

    private void updateKFilter() {
        double[] stateCorrection = new double[]{this.x, this.y, this.vx, this.vy, this.ax, this.ay};
        this.trackingFilter.predict();      // this needs to be called before calling correct()
        if (DEBUG) {System.out.println(String.format("\nUpdating filter: %d %d %.4f %.4f", this.x, this.y, this.vx, this.vy));}
        this.trackingFilter.correct(stateCorrection);
        if (DEBUG) {System.out.println(String.format("Prediction: %s", Arrays.toString(this.trackingFilter.getStateEstimation())));}
    }

    private double[] getPredictedState() {
        this.trackingFilter.predict();
        return this.trackingFilter.getStateEstimation();
//        double[] predictedState = this.trackingFilter.getStateEstimation();
//        this.trackingFilter.correct(predictedState);        // this is already called when predictTrajectory() calls updateLocation
//        return predictedState;
    }

    private enum MovementState {
        INMOTION, STATIONARY, STARTLED
    }

    public Iterator<int[]> getLinePointsIterator() {      // TODO  figure out how to use this instead?   -->   use linePoints.size() to know when to stop
        return linePoints.iterator();
    }

    public Iterator<double[]> getDataPointsIterator() {      // TODO  figure out how to use this instead?   -->   use linePoints.size() to know when to stop
        return dataPoints.iterator();
    }
}
