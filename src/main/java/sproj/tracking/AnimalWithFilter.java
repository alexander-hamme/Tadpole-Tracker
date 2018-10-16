package sproj.tracking;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.math3.filter.KalmanFilter;
import org.bytedeco.javacpp.opencv_core.Scalar;
import sproj.util.BoundingBox;

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
    public double ax, ay;   // acceleration
    public double currentHeading;
    public MovementState movementState;
    public KalmanFilter trackingFilter;
    private int[] positionBounds = new int[4];

    private boolean PREDICT_WITH_VELOCITY = true;

    public AnimalWithFilter(int x, int y, int[] positionBounds, Scalar clr, KalmanFilter kFilter) {
        this.x = x; this.y = y;
        this.positionBounds = positionBounds;
        currentHeading = 0;
        color = clr; // new Scalar(clr[0], clr[1], clr[2], 1.0);
        linePoints = new CircularFifoQueue<>(LINE_POINTS_SIZE);
        dataPoints = new ArrayList<>(DATA_BUFFER_ARRAY_SIZE);
        trackingFilter = kFilter;
    }


    public void updateLocation(int x, int y, double dt, long timePos) {

        this.x = x; this.y = y;
        applyBoundsConstraints();
        dataPoints.add(new double[]{this.x, this.y, timePos});
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

        //TODO keep track of how many subsequent frames have been predicted, if greater than (5, or 10, etc),
        // todo       temporarily increase assigner's DEFAULT_COST_OF_NON_ASSIGNMENT (for this specific animal??)  to get back on track


        // TODO  --> each animal should have its own dynamic DEFAULT_COST_OF_NON_ASSIGNMENT!!!

        double[] predictedState = getPredictedState();
        System.out.println(String.format("Current [(%d,%d)(%.3f,%.3f)], estimation: %s",
                this.x,this.y, this.vx, this.vy, Arrays.toString(predictedState))
        );

        double predX = predictedState[0]; //(int) Math.round(predictedState[0]);
        double predY = predictedState[1]; //(int) Math.round(predictedState[1]);
        double vx = predictedState[2];
        double vy = predictedState[3];

        int newx, newy;
        // Simplest method
        if (! PREDICT_WITH_VELOCITY) {
            newx = (int) Math.round(predictedState[0]);
            newy = (int) Math.round(predictedState[1]);
        } else {
            newx = (int) Math.round(this.x + (vx * dt));
            newy = (int) Math.round(this.y - (vy * dt));
        }


//        int newx = (int) Math.round((predX + (this.x + (vx * dt))) / 2);    // average the predicted position with calculated position from predicted velocity
//        int newy = (int) Math.round((predY + (this.y + (vy * dt))) / 2);


        System.out.println(String.format("new coordinates: (%d, %d)", newx, newy));



        // todo method 3:  use predicted position to calculate heading, and then factor in velocity to predict position?



        updateLocation(newx, newy, dt, timePos);
    }


    /** Use line points because it keeps track of only the most recent points */
    private void updateVelocity(double dt) {        // TODO use timePos points to calculate dt!

        int subtractionIdx = 3;  // calculate velocity over the last N frames
        if (linePoints.size() < subtractionIdx + 1) {
            this.vx = 0;
            this.vy = 0;

        } else {

            // todo double dt = timePos - timePosPrev

            // todo average these out so the change in values isnt so drastic
            int[] prevPoint = linePoints.get(linePoints.size() - 1 - subtractionIdx);   // the most recent point is at the end index
            this.vx = (this.x - prevPoint[0]) / (subtractionIdx * dt);


            // Have to flip the subtraction because y axis in graphics increases by going down instead of up
             this.vy = ((prevPoint[1] - this.y) / (subtractionIdx * dt));
//            this.vy = -1 * ((this.y - prevPoint[1]) / (subtractionIdx * dt));

            /*
            System.out.println(String.format("Current (%d, %d) from %sdx: %d dy: %d,   Velocity: %.4f %.4f",
                    this.x, this.y, Arrays.toString(prevPoint), this.x - prevPoint[0], this.y - prevPoint[1], this.vx, this.vy)
            );
            */

//            this.vx *= 1000;
//            this.vy *= 1000;
        }
    }

    private void updateKFilter() {
        double[] stateCorrection = new double[]{this.x, this.y, this.vx, this.vy, this.ax, this.ay};
        this.trackingFilter.predict();      // this needs to be called before calling correct()
        System.out.println(String.format("\nUpdating filter: %d %d %.4f %.4f", this.x, this.y, this.vx, this.vy));
        this.trackingFilter.correct(stateCorrection);
        System.out.println(String.format("Prediction: %s", Arrays.toString(this.trackingFilter.getStateEstimation())));
    }

    private double[] getPredictedState() {
        this.trackingFilter.predict();
//        return this.trackingFilter.getStateEstimation();
        double[] predictedState = this.trackingFilter.getStateEstimation();
//        this.trackingFilter.correct(predictedState);        // this is already called when predictTrajectory() calls updateLocation
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