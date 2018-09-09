package sproj.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

public class ImageJFrame extends JFrame {


    private class ImageFrame extends Component {
        private BufferedImage image;

        public void paint(Graphics g) {
            g.drawImage(image, 0, 0, null);
        }

        public void updateImage(BufferedImage img) {
            this.image = img;
        }
    }

    public ImageJFrame() {       // todo add parameters

        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        this.add(new ImageFrame());
        this.pack();
        this.setVisible(true);
    }
}
