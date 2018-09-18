package sproj.tracking;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.bytedeco.javacpp.opencv_core.Scalar;
import java.util.ArrayList;
import java.util.Iterator;

public class Animal {

    public final Scalar color;
    public final int LINE_THICKNESS = 2;
    public final int CIRCLE_RADIUS = 15;
    public final int LINE_POINTS_SIZE = 16;
    public final static int DATA_BUFFER_ARRAY_SIZE = 60;

    private ArrayList<double[]> dataPoints;
    private CircularFifoQueue<int[]> linePoints;

    public int x, y;
    public double currentHeading;
    public MovementState movementState;

    public Animal(int x, int y, int[] clr) {
        this.x = x;
        this.y = y;
        this.currentHeading = 0;
        this.color = new Scalar(clr[0], clr[1], clr[2], 1.0);
        this.linePoints = new CircularFifoQueue<>(LINE_POINTS_SIZE);
        this.dataPoints = new ArrayList<>(DATA_BUFFER_ARRAY_SIZE);
    }

    public void updateLocation(int x, int y, long timePos) {
        dataPoints.add(new double[]{x, y, timePos});
        linePoints.add(new int[]{x, y});   // calls the addFirst() method, adds to front of Deque
        this.x = x; this.y = y;
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


/*    public int[][] getLinePointsAsArray() {

        pointsIterator = linePoints.iterator();

        for (int i=0; i<linePoints.size(); i++) {
            linePointsArray[i] = (int[]) pointsIterator.next();
        }
        return linePointsArray;
    }
    public double[][] getDataPoints() {
        return (double[][]) dataPoints.toArray();
    }*/

}