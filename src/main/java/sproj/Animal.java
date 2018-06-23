package sproj;

import java.util.ArrayDeque;
import java.util.ArrayList;

import java.lang.Math;

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

        calcKinematics(x, y, t);  // this.angle = ...

        this.dataPoints.add(new double[]{t, x, y, this.angle});

        this.linePoints.add(new int[]{x, y});
    }

    private void calcKinematics(int x, int y, double t) {
        // calculate more than just angle

        if (this.dataPoints.size() < BUFF_INDX) {
            return;
        }

        ArrayList<double[]> prevPoints = (ArrayList<double[]>) this.dataPoints.subList(
                this.dataPoints.size() - BUFF_INDX, this.dataPoints.size()
        );

        // time elapsed between current point and the first in list
        double deltaTime = (t - prevPoints.get(0)[0]) / 1000.0;  // convert from milliseconds to seconds  so kinematics values are greater than zero

        double dx = x - prevPoints.get(0)[0];
        double dy = (-1) * y - prevPoints.get(0)[1];  // fix (computer graphics) y axis so increasing is up and decreasing is down

        double vx = dx / deltaTime;
        double vy = dy / deltaTime;

        this.angle = 180 / Math.PI * Math.atan2(vy, vx);


    }

    private double[] predictTrajectory() {
        // // TODO: 6/23/18        RNN Prediction here!!!



        return null;
    }


}
