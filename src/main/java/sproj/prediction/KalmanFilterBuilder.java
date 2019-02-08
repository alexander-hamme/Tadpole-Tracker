package sproj.prediction;


import org.apache.commons.math3.filter.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 * Builds an instance of the Apache Commons KalmanFilter with custom matrix values
 */
public class KalmanFilterBuilder {

    /*
     * TODO
     * for some reason the second 2 values are not being predicted accurately,
     * but whichever values are in the first 2 spaces of the array are. Figure out why.
     */

    private final double sv = 0.8;          // sensitivity value for state transition matrix.  --> the closer to 1.0 this is,
                                            // the faster the filter adjusts to changes in data, which affects estimation accuracy
    public KalmanFilterBuilder() {
    }

    public KalmanFilter getNewKalmanFilter(double x0, double y0, double vx0, double vy0) {

        double placeHolder = 0.0;

        RealVector initialStateEstimate_xHat  = new ArrayRealVector(
                new double[]{x0,y0,vx0,vy0,placeHolder,placeHolder}                 // initial position and velocity values
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

    // todo add explanation of values

    private final RealMatrix stateTransition_mA = new Array2DRowRealMatrix(
            new double[][]{
                    {1, 0, 0, 0, sv, 0},
                    {0, 1, 0, 0, 0, sv},
                    {0, 0, 1, 0, 0,  0},
                    {0, 0, 0, 1, 0,  0},
                    {0, 0, 0, 0, 1,  0},
                    {0, 0, 0, 0, 0,  1}
            });

    private final RealMatrix inputControl_mB = new Array2DRowRealMatrix(  // identity matrix
            new double[][]{
                    {1, 0, 0, 0, 0, 0},
                    {0, 1, 0, 0, 0, 0},
                    {0, 0, 1, 0, 0, 0},
                    {0, 0, 0, 1, 0, 0},
                    {0, 0, 0, 0, 1, 0},
                    {0, 0, 0, 0, 0, 1},
            });

    // influences kalman gain
    private final RealMatrix measurementMatrix_mH = new Array2DRowRealMatrix(
            new double[][]{
                    {1, 0, 0, 0, 1, 0},
                    {0, 1, 0, 0, 0, 1},
                    {0, 0, 1, 0, 0, 0},
                    {0, 0, 0, 1, 0, 0},
                    {0, 0, 0, 0, 1, 0},
                    {0, 0, 0, 0, 0, 1}
            });

    private final RealMatrix actionUncertainty_mQ = new Array2DRowRealMatrix(
            new double[][]{
                    {0.1, 0, 0, 0, 0, 0},
                    {0, 0.1, 0, 0, 0, 0},
                    {0, 0, 0.1, 0, 0, 0},
                    {0, 0, 0, 0.1, 0, 0},
                    {0, 0, 0, 0, 0.1, 0},
                    {0, 0, 0, 0, 0, 0.1}
            });

    private final RealMatrix sensorNoise_mR = new Array2DRowRealMatrix(
            new double[][]{
                    {0.1, 0, 0, 0, 0, 0},
                    {0, 0.1, 0, 0, 0, 0},
                    {0, 0, 0.1, 0, 0, 0},
                    {0, 0, 0, 0.1, 0, 0},
                    {0, 0, 0, 0, 0.1, 0},
                    {0, 0, 0, 0, 0, 0.1}
            });

    private final RealMatrix initialStateCovariance_mP =  new Array2DRowRealMatrix(  // zeros matrix
            new double[][]{
                    {0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0}
            });

    private final RealVector controlVector = new ArrayRealVector(       // not used
            new double[]{0,0,0,0,0,0}
    );
}