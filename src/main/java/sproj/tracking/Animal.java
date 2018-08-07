package sproj.tracking;

import org.bytedeco.javacpp.opencv_core.Scalar;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class Animal {

    public int x, y;
    public final Scalar color;
    public static int BUFF_INDEX = 60;
    public ArrayList<double[]> dataPoints;
    private final int linePointsSize = 16;
    private ArrayDeque<int[]> linePoints;

    public Animal(int x, int y, int[] clr) {
        this.x = x;
        this.y = y;
        this.color = new Scalar(clr[0], clr[1], clr[2], 1.0);
        this.linePoints = new ArrayDeque<>(linePointsSize);
        this.dataPoints = new ArrayList<>(BUFF_INDEX);
    }

    public void updateLocation(int x, int y, long timePos) {
        dataPoints.add(new double[]{x, y, timePos});
        linePoints.push(new int[]{x, y});   // calls the addFirst() method, adds to front of Deque
        this.x = x; this.y = y;
    }

    public int[][] getLinePointsAsArray() {
        return (int[][]) this.linePoints.toArray();
    }




}