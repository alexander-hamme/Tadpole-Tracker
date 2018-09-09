package sproj.util;

import org.bytedeco.javacv.CanvasFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class TestApp {


    // https://www.codejava.net/java-se/swing/jpanel-basic-tutorial-and-examples

    public static void setUpDisplay() {

        /** note that JPanels and other objects can be nested */
    }

    public static void main(String[] args) throws IOException {

        // set look and feel to the system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new TrackerCanvasFrame("SinglePlateTracker");

        });


        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                    ex.printStackTrace();
                }

                JFrame frame = new JFrame("Testing");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(new ImageJPanel());
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);         // this must alwasy be called last
            }
        });

        /*javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setUpDisplay();
            }
        });*/
    }
}
