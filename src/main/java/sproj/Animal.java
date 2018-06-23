package sproj;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class Animal {

    final int BUFF_INDX = 3;        // buffer of data points to hold back when data is flushed to file, to run calculations on
    final int DEFAULT_BUFF = 24;    // length of line trailing behind each animal in graphic window

    int x, y;
    int radius;
    double angle;

    int[] color;

    ArrayDeque<int[]> linePoints;
    ArrayList<double[]> dataPoints;

    public Animal(int x, int y, int r, int[] clr) {
        this.x = x;
        this.y = y;
        this.radius = r;
        this.angle = 0.0;
        this.color = clr;

        this.linePoints = new ArrayDeque<int[]>(DEFAULT_BUFF);
        this.dataPoints = new ArrayList<double[]>();

    }

    protected void updateLocation(int x, int y, double t) {
        // update kinematics values and append new data points
        this.x = x;
        this.y = y;

        this.angle = calcKinematics(x, y, t);

        this.dataPoints.add(new double[]{t, x, y, this.angle});

        this.linePoints.add(new int[]{x, y});
    }

    private double calcKinematics(int x, int y, int t) {
        // calculate more than just angle
    }

    private double predictTrajectory() {
        // // TODO: 6/23/18        RNN Prediction here!!!
    }


}
