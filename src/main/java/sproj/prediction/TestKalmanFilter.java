package sproj.prediction;


import org.apache.commons.math3.filter.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_video;
import sproj.util.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestKalmanFilter {

    public static void main(String[] args) throws IOException {

        double dt = 1.0/30;    // 30 frames per second

        double initialX = 0.0;  // animal.x
        double initialY = 0.0;  // animal.y
        double initialXVel = 0.0;
        double initialYVel = 0.0;


        RealMatrix stateTransition_mA = new Array2DRowRealMatrix(                        // not used
                new double[][]{
                        {1, 0, dt, 0},
                        {0, 1, 0, dt},
                        {0, 0, 1, 0},
                        {0, 0, 0, 1}
        });

        RealMatrix inputControl_mB = new Array2DRowRealMatrix(                        // just an identity matrix
                new double[][]{
                        {1, 0, 0, 0},
                        {0, 1, 0, 0},
                        {0, 0, 1, 0},
                        {0, 0, 0, 1}
        });



        // influences kalman gain
        RealMatrix measurementMatrix_mH = new Array2DRowRealMatrix(  //new double[]{initialX, initialY, initialXVel, initialYVel}); // measurement vector
                new double[][]{
                        {1, 0, 1, 0},      // todo figure out why these numbers?
                        {0, 1, 0, 1},
                        {0, 0, 0, 0},
                        {0, 0, 0, 0}
        });


        RealMatrix actionUncertainty_mQ = new Array2DRowRealMatrix(
                new double[][]{
                        {0, 0, 0,   0},
                        {0, 0, 0,   0},
                        {0, 0, 0.1, 0},
                        {0, 0, 0,   0.1}
        });


        RealMatrix sensorNoise_mR = new Array2DRowRealMatrix(           // todo dont use this??
                new double[][]{
                        {0, 0, 0,  0},
                        {0, 0, 0,  0},
                        {0, 0,0.1, 0},
                        {0, 0, 0, 0.1}
        });


        RealVector controlVector = new ArrayRealVector(                        // not used
                new double[]{0,0,0,0}
        );


        RealVector initialStateEstimate_xHat = new ArrayRealVector(                        // not used
                new double[]{initialX,initialY,initialXVel,initialYVel}
        );

        RealMatrix initialStateCovariance_mP =  new Array2DRowRealMatrix(                        // zeros matrix
                new double[][]{
                        {0, 0, 0, 0},
                        {0, 0, 0, 0},
                        {0, 0, 0, 0},
                        {0, 0, 0, 0}
        });


//        ProcessModel pm = new DefaultProcessModel(stateTransition_mA, inputControl_mB, actionUncertainty_mQ, xVector, mP); // xVector, mP

        // parameters: stateTransition, control, processNoise     semi-optional: (initialStateEstimate, initialErrorCovariance)
        // KalmanFilter sets the initial state estimate to a zero vector if it is not available from the process model
        ProcessModel pm = new DefaultProcessModel(
                stateTransition_mA, inputControl_mB, actionUncertainty_mQ, initialStateEstimate_xHat, initialStateCovariance_mP); // xVector, mP

        // parameters:  measurementMatrix,  measurementNoise
        MeasurementModel mm = new DefaultMeasurementModel(measurementMatrix_mH, sensorNoise_mR);
        KalmanFilter filter = new KalmanFilter(pm, mm);




        List<String> lines = IOUtils.readLinesFromFile("/home/ah2166/Documents/sproj/tracking_data/motionData/testData1.dat");
        List<double[]> dataPoints = new ArrayList<>(lines.size());

        for (String line : lines) {
            String[] split = line.split(",");
            if (split.length > 2) continue;
            try {
                dataPoints.add(new double[]{Double.valueOf(split[0]), Double.valueOf(split[1])});
            } catch (NumberFormatException ignored) {
                // a few lines in the file are headers and do not contain data points
                System.out.println("Could not format number in line: " + line);
            }
        }



//        for (double[] point: dataPoints) {
        for (int i=1; i<dataPoints.size(); i++) {

            double[] point = dataPoints.get(i);
            double[] lastPoint = dataPoints.get(i-1);
            double[] velocity = new double[] {
                    (point[0]-lastPoint[0]) / dt, (point[1]-lastPoint[1]) / dt
            };


            RealVector nextRealMeasurement = new ArrayRealVector(new double[]{point[0], point[1], velocity[0], velocity[1]}); // x, y, velx, vely

            // predict the state estimate one time-step ahead
            // optionally provide some control input
            filter.predict();

            filter.correct(nextRealMeasurement);

            double[] stateEstimate = filter.getStateEstimation();    // x hat


            System.out.println(String.format(
                    "\nReal values: \t\t%.3f\t%.3f\t%.3f\t%.3f",
                    point[0], point[1], velocity[0], velocity[1]
            ));


            System.out.println(String.format(
//                    "Current estimated values are: {x: %.3f, y: %.3f, vx: %.3f, vy: %.3f",
                    "Estimated values: \t%.3f\t%.3f\t%.3f\t%.3f",
                    stateEstimate[1], stateEstimate[1], stateEstimate[0], stateEstimate[1]
            ));
            /*
            if no detection for a given frame, pass in the previous estimate in to the filter for estimation?

            predX = x
            predX = (A * predX) + (B * c)

            and the new estimation is in the x vector
             */
        }
    }
}
