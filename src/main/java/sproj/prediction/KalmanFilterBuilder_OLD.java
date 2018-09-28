package sproj.prediction;


import org.apache.commons.math3.filter.*;
import org.apache.commons.math3.linear.*;
import sproj.util.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KalmanFilterBuilder_OLD {

    /**
     *
     * todo     for some reason the second 2 values are not being predicted accurately,
     * todo     but whichever values are in the first 2 spaces of the array are
     *
     * todo    just make an extra filter for the velocity?
     *
     *
     */

    private final double stv = 0.4;         // sensitivity value for state transition matrix.  --> the closer to 1.0 this is, <--todo ????
                                            // the faster the filter adjusts to changes in data, which affects estimation accuracy
                                            // todo explain & justify value
    public KalmanFilterBuilder_OLD() {
        // take parameters?
    }

    public KalmanFilter getNewKalmanFilter(double x0, double y0, double vx0, double vy0) {

        RealVector initialStateEstimate_xHat  = new ArrayRealVector(
                new double[]{x0,y0,vx0,vy0}                 // initial position and velocity values
        );

        ProcessModel pM = getProcessModel(initialStateEstimate_xHat);
        MeasurementModel mM = getMeasurementModel();
        return new KalmanFilter(pM, mM);

    }

    private ProcessModel getProcessModel(RealVector initialStateEstimate_xHat) {
        return new DefaultProcessModel(
                stateTransition_mA, inputControl_mB, actionUncertainty_mQ,
                initialStateEstimate_xHat, initialStateCovariance_mP);
    }

    private MeasurementModel getMeasurementModel() {
        return new DefaultMeasurementModel(measurementMatrix_mH, sensorNoise_mR);
    }

    private final RealMatrix stateTransition_mA = new Array2DRowRealMatrix(
            new double[][]{
                    {1, 0, stv, 0},
                    {0, 1, 0, stv},
                    {0, 0, 1, 0},
                    {0, 0, 0, 1}
            });

    private final RealMatrix inputControl_mB = new Array2DRowRealMatrix(                     // identity matrix
            new double[][]{
                    {1, 0, 0, 0},
                    {0, 1, 0, 0},
                    {0, 0, 1, 0},
                    {0, 0, 0, 1}
            });

    // influences kalman gain
    private final RealMatrix measurementMatrix_mH = new Array2DRowRealMatrix(
            new double[][]{
                    {1, 0, 1, 0},   // 1,0,1,0    // todo explain this configuration of numbers
                    {0, 1, 0, 1},   // 0,1,0,1
                    {0, 0, 1, 0},
                    {0, 0, 0, 1}
            });

    private final RealMatrix actionUncertainty_mQ = new Array2DRowRealMatrix(
            new double[][]{
                    {0, 0, 0, 0},    // 0.1,0,0,0?  todo
                    {0, 0, 0, 0},    // 0,0.1,0,0
                    {0, 0, 0.1, 0},       // 0,0,0.1,0
                    {0, 0, 0, 0.1}        // 0,0,0,0.1
            });

    private final RealMatrix sensorNoise_mR = new Array2DRowRealMatrix(
            new double[][]{
                    {0, 0, 0,  0},    // 0.1,0,0,0
                    {0, 0, 0,  0},    // 0,0.1,0,0
                    {0, 0,0.1, 0},
                    {0, 0, 0, 0.1}
            });

    private final RealMatrix initialStateCovariance_mP =  new Array2DRowRealMatrix(       // zeros matrix
            new double[][]{
                    {0, 0, 0, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            });

    private final RealVector controlVector = new ArrayRealVector(                        // not used
            new double[]{0,0,0,0}
    );


    public static void main(String[] args) throws IOException {
        testData();
    }



    public static void testData() throws IOException {

        double dt = 1.0/30;    // 30 frames per second

        double initialX = 0.0;  // animal.x
        double initialY = 0.0;  // animal.y
        double initialXVel = 0.0;
        double initialYVel = 0.0;


        double stv = 0.9;   // state transition value.  --> the closer to 1.0 this is, the faster the filter adjusts to changes in data,
                            // which affects estimation accuracy

        RealMatrix stateTransition_mA = new Array2DRowRealMatrix(
                new double[][]{
                        {1, 0, stv, 0},
                        {0, 1, 0, stv},
                        {0, 0, 1, 0},
                        {0, 0, 0, 1}
        });

        RealMatrix inputControl_mB = new Array2DRowRealMatrix(                     // just an identity matrix
                new double[][]{
                        {1, 0, 0, 0},
                        {0, 1, 0, 0},
                        {0, 0, 1, 0},
                        {0, 0, 0, 1}
        });



        // influences kalman gain
        RealMatrix measurementMatrix_mH = new Array2DRowRealMatrix(
                new double[][]{
                        {1, 0, 1, 0},      // todo figure out why this configuration of numbers
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


        RealVector initialStateEstimate_xHat = new ArrayRealVector(
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


        // Todo   currently it is not adapting quickly enough to the changes in data, what do you need to change to fix this?


//        for (double[] point: dataPoints) {
        for (int i=1; i<dataPoints.size(); i++) {

            double[] point = dataPoints.get(i);
            double[] lastPoint = dataPoints.get(i-1);
            double[] velocity = new double[] {
                    100 + (point[0]-lastPoint[0]) / 1, 100 + (point[1]-lastPoint[1]) / 1        // don't use dt as a divider?
            };


            RealVector nextRealMeasurement = new ArrayRealVector(new double[]{point[0], point[1], velocity[0], velocity[1]}); // x, y, velx, vely

            // predict the state estimate one time-step ahead
            // optionally provide some control input
            filter.predict();

            if (i%25 == 0) {
                System.out.println("\nNo real measurement update this time");
            } else {
                filter.correct(nextRealMeasurement);

                System.out.println(String.format(
                        "\nReal values: \t\t%.3f\t%.3f\t%.3f\t%.3f",
                        point[0], point[1], velocity[0], velocity[1]
                ));
            }

            double[] stateEstimate = filter.getStateEstimation();    // x hat


            System.out.println(String.format(
//                    "Current estimated values are: {x: %.3f, y: %.3f, vx: %.3f, vy: %.3f",
                    "Estimated values: \t%.3f\t%.3f\t%.3f\t%.3f",
                    stateEstimate[0], stateEstimate[1], stateEstimate[2], stateEstimate[3]
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
