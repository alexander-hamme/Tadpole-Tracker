package sproj.tracking;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.bytedeco.javacpp.opencv_core.Scalar;
import java.util.ArrayList;
import java.util.Iterator;

public class Animal {

    public int x, y;
    public final Scalar color;
    public final int LINE_THICKNESS = 2;
    public final int CIRCLE_RADIUS = 15;
    public static int BUFF_INDEX = 60;
    public final int linePointsSize = 16;
    private ArrayList<double[]> dataPoints;
    private CircularFifoQueue<int[]> linePoints;

    private int[][] linePointsArray;
    private Iterator pointsIterator;

    public Animal(int x, int y, int[] clr) {
        this.x = x;
        this.y = y;
        this.color = new Scalar(clr[0], clr[1], clr[2], 1.0);
        this.linePoints = new CircularFifoQueue<>(linePointsSize);
        this.dataPoints = new ArrayList<>(BUFF_INDEX);

        linePointsArray = new int[linePointsSize][];
    }

    public void updateLocation(int x, int y, long timePos) {
        dataPoints.add(new double[]{x, y, timePos});
        linePoints.add(new int[]{x, y});   // calls the addFirst() method, adds to front of Deque
        this.x = x; this.y = y;
    }

    public Iterator<int[]> getLinePointsIterator() {      // TODO  figure out how to use this instead?   -->   use linePoints.size() to know when to stop
        return linePoints.iterator();
    }

    public int[][] getLinePointsAsArray() {      // TODO  don't do this, just pass an Iterator of the Queue!!!

        pointsIterator = linePoints.iterator();

        for (int i=0; i<linePoints.size(); i++) {
            linePointsArray[i] = (int[]) pointsIterator.next();
        }
        return linePointsArray;
    }

    public Iterator<double[]> getDataPointsIterator() {      // TODO  figure out how to use this instead?   -->   use linePoints.size() to know when to stop
        return dataPoints.iterator();
    }


    public double[][] getDataPoints() {
        return (double[][]) dataPoints.toArray();
    }

}