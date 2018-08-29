package sproj.util;

import org.bytedeco.javacv.CanvasFrame;

import javax.swing.*;

public class TestApp {


    public static void setUpDisplay() {


        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, BoxLayout.PAGE_AXIS);
        panel.setLayout(layout);
        panel.setOpaque(true);

        CanvasFrame canvas = new CanvasFrame("TRACKER", 1.0);               // gamma: CanvasFrame.getDefaultGamma()/grabber.getGamma());
        canvas.setCanvasSize(700, 700);                    // WINDOW_WIDTH, WINDOW_HEIGHT);

        canvas.setLocationRelativeTo(null);     // centers the window
        canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);    // Exit application when window is closed.
        canvas.setResizable(true);
        canvas.setLayout(layout);

        canvas.setContentPane(panel);
//        canvas.getContentPane().add(canvas);
        // add components
//        canvas.getContentPane().add();
//        canvas.getContentPane().add(panel);
        canvas.pack();

        canvas.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setUpDisplay();
            }
        });
    }
}
