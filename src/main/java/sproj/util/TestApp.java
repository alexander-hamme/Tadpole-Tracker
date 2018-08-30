package sproj.util;

import org.bytedeco.javacv.CanvasFrame;

import javax.swing.*;
import java.awt.*;

public class TestApp {

    // https://www.codejava.net/java-se/swing/jpanel-basic-tutorial-and-examples

    public static void setUpDisplay() {

        /** note that JPanels and other objects can be nested */
    }

    public static void main(String[] args) {

        // set look and feel to the system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                new TrackerCanvasFrame("Tracker").setVisible(true);
            }
        });

        /*javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setUpDisplay();
            }
        });*/
    }
}
