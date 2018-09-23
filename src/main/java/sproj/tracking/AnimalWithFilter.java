package sproj.tracking;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.math3.filter.KalmanFilter;
import org.bytedeco.javacpp.opencv_core.Scalar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class AnimalWithFilter {

    public final Scalar color;
    public final int LINE_THICKNESS = 2;
    public final int CIRCLE_RADIUS = 15;
    public final int LINE_POINTS_SIZE = 16;
    public final static int DATA_BUFFER_ARRAY_SIZE = 60;

    private ArrayList<double[]> dataPoints;
    private CircularFifoQueue<int[]> linePoints;

    public int x, y;
    public double vx, vy;
    public double currentHeading;
    public MovementState movementState;
    public KalmanFilter trackingFilter;

    public AnimalWithFilter(int x, int y, int[] clr, KalmanFilter kFilter) {
        this.x = x; this.y = y;
        currentHeading = 0;
        color = new Scalar(clr[0], clr[1], clr[2], 1.0);
        linePoints = new CircularFifoQueue<>(LINE_POINTS_SIZE);
        dataPoints = new ArrayList<>(DATA_BUFFER_ARRAY_SIZE);
        trackingFilter = kFilter;
    }

    public void updateLocation(int x, int y, double dt, long timePos) {
        dataPoints.add(new double[]{x, y, timePos});
        linePoints.add(new int[]{x, y});   // calls the addFirst() method, adds to front of Deque
        this.x = x; this.y = y;
        updateVelocity(dt);
        updateKFilter();
    }


    public void predictTrajectory(double dt, long timePos) {

        double[] predictedState = getPredictedState();
        System.out.println("(trajectory estimation) (" + this.x + ", " + this.y + ") " + Arrays.toString(predictedState));


        double vx = predictedState[2];
        double vy = predictedState[3];

        // todo incorporate velocity into better prediction somehow

        int newx = (int) Math.round(predictedState[0]);
        int newy = (int) Math.round(predictedState[1]);
//        int newx = (int) Math.round((predictedState[0] + (this.x + (vx))) / 2);
//        int newy = (int) Math.round((predictedState[1] + (this.y + (vy))) / 2);

//                animal.updateLocation((int)Math.round(predictedState[0]), (int)Math.round(predictedState[1]), timePos);
        updateLocation(newx, newy, dt, timePos);
    }


    /** Use line points because it keeps track of only the most recent points */
    private void updateVelocity(double dt) {

        int subtractionIdx = 3;  // calculate velocity over the last N frames
        if (linePoints.size() < subtractionIdx) {
            this.vx = 0;
            this.vy = 0;
        } else {
            // todo***  the most recent point is at the end index!!  ***

            // todo average these out so the change in values isnt so drastic
            int[] prevPoint = linePoints.get(linePoints.size() - subtractionIdx);
            this.vx = (this.x - prevPoint[0]) / dt;
            this.vy = (this.y - prevPoint[1]) / dt;
        }
    }

    private void updateKFilter() {
        this.trackingFilter.predict();      // this needs to be called before calling correct()
        this.trackingFilter.correct(new double[]{this.x, this.y, this.vx, this.vy});
    }

    private double[] getPredictedState() {
        this.trackingFilter.predict();
//        return this.trackingFilter.getStateEstimation();
        double[] predictedState = this.trackingFilter.getStateEstimation();
        this.trackingFilter.correct(predictedState);        // TODO is doing this correct?
        return predictedState;
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