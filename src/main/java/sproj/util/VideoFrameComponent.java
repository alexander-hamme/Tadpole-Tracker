package sproj.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

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