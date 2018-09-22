package sproj.tracking;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.math3.filter.KalmanFilter;
import org.bytedeco.javacpp.opencv_core.Scalar;

import java.util.ArrayList;
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
        this.x = x;
        this.y = y;
        this.currentHeading = 0;
        this.color = new Scalar(clr[0], clr[1], clr[2], 1.0);
        this.linePoints = new CircularFifoQueue<>(LINE_POINTS_SIZE);
        this.dataPoints = new ArrayList<>(DATA_BUFFER_ARRAY_SIZE);
        this.trackingFilter = kFilter;
    }

    public void updateLocation(int x, int y, long timePos) {
        dataPoints.add(new double[]{x, y, timePos});
        linePoints.add(new int[]{x, y});   // calls the addFirst() method, adds to front of Deque
        this.x = x; this.y = y;
        updateVelocity();
        updateKFilter();
    }

    public double[] predictTrajectory() {
        this.trackingFilter.predict();
        return this.trackingFilter.getStateEstimation();
    }

    /** Use line points because it keeps track of only the most recent points */
    private void updateVelocity() {

        int subtractionIdx = 3;  // todo calculate velocity over the last (3?) frames
        if (linePoints.size() < subtractionIdx) {
            this.vx = 0;
            this.vy = 0;
        } else {
            // TODO  is the most recent point at the end index or at index 0???
            double dt_divider = 1.0;  // use   1.0 / framerate?
            int[] prevPoint = linePoints.get(subtractionIdx);
            this.vx = (this.x - prevPoint[0]) / dt_divider;     // todo average them out?
            this.vy = (this.y - prevPoint[1]) / dt_divider;
        }
    }

    private void updateKFilter() {
        this.trackingFilter.predict();      // this needs to be called before calling correct()
        this.trackingFilter.correct(new double[]{this.x, this.y, this.vx, this.vy});
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