package sproj.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoFrameComponent extends JComponent {

    BufferedImage frameImage;

    public void updateImage(BufferedImage img) {
        frameImage = img;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(frameImage, 0, 0, null);
    }

    @Override
    public void repaint() {
        super.repaint();
    }
}