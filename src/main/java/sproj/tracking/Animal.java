package sproj.tracking;

import org.bytedeco.javacpp.opencv_core.Scalar;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

public class Animal {

    public int x, y;
    public final Scalar color;
    public final double lineThicknessBuffer = 5.0;
    public static int BUFF_INDEX = 60;
    private ArrayList<double[]> dataPoints;
    private final int linePointsSize = 16;
    private ArrayDeque<int[]> linePoints;

    private int[][] linePointsArray;
    private Iterator pointsIterator;

    public Animal(int x, int y, int[] clr) {
        this.x = x;
        this.y = y;
        this.color = new Scalar(clr[0], clr[1], clr[2], 1.0);
        this.linePoints = new ArrayDeque<>(linePointsSize);
        this.dataPoints = new ArrayList<>(BUFF_INDEX);

        linePointsArray = new int[linePointsSize][];
    }

    public void updateLocation(int x, int y, long timePos) {
        dataPoints.add(new double[]{x, y, timePos});
        linePoints.push(new int[]{x, y});   // calls the addFirst() method, adds to front of Deque
        this.x = x; this.y = y;
    }

    public int[][] getLinePointsAsArray() {      // TODO  don't do this, just pass the Deque and iterate through it?

        pointsIterator = linePoints.iterator();

        for (int i=0; i<linePoints.size(); i++) {
            linePointsArray[i] = (int[]) pointsIterator.next();
        }
        return linePointsArray;
    }


    public double[][] getDataPoints() {
        return (double[][]) dataPoints.toArray();
    }

}