package sproj.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageJPanel extends JPanel {

    BufferedImage frameImage = null;

    public void updateImage(BufferedImage img) {
        frameImage = img;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frameImage != null) {
            g.drawImage(frameImage, 0, 0, this);
        }
    }
}