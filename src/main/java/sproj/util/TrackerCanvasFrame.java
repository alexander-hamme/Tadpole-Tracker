package sproj.util;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
//import org.bytedeco.javacv.Frame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;

public class TrackerCanvasFrame extends CanvasFrame {

    ImageJPanel imagePanel;
    

    private static BufferedImage makeTestImage() throws IOException {

        if (1==1) {

            return ImageIO.read(new File("src/main/resources/images/test_image.png"));
        }

        // test starting from video file with FFmpegGrabber

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("src/main/resources/videos/IMG_3086.MOV");
        grabber.start();    // open video file


        org.bytedeco.javacv.Frame frame;
        if ((frame = grabber.grabImage()) == null) {
            return null;
        }


        Java2DFrameConverter paintConverter = new Java2DFrameConverter();
        return paintConverter.getBufferedImage(frame);


    }

    public TrackerCanvasFrame(String title) {
        super(title);       // todo  add Gamma / screen number / other constructor args here?
//        addComponents();
//        setVisible(true);   put this in the App class?
    }

    private void addComponents() throws IOException {
        JPanel jPanel = new JPanel(new GridBagLayout());

        JLabel labelUsername = new JLabel("Enter username: ");
        JLabel labelPassword = new JLabel("Enter password: ");
        JTextField textUsername = new JTextField(20);
        JPasswordField fieldPassword = new JPasswordField(20);
        JButton buttonLogin = new JButton("Login");


        ImageIcon imgIcon = new ImageIcon("src/main/resources/images/test_image.png");
//        imgIcon.setImage();


        imagePanel = new ImageJPanel();
        imagePanel.setOpaque(true);
//        imagePanel.setSize(500,500);


        BufferedImage testImg = null;
        try {
            testImg = makeTestImage();
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert testImg != null;
        Graphics2D g2d = testImg.createGraphics();
//        imageHolder.add(g2d);

        try {
            imagePanel.updateImage(makeTestImage());
        } catch (IOException e) {
            e.printStackTrace();
        }




        JLabel imageHolder = new JLabel();
        imageHolder.imageUpdate(makeTestImage(), 0, 0, 0, 0, 0);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 10, 10, 10);

        // add components to the panel
        constraints.gridx = 0;
        constraints.gridy = 0;
        jPanel.add(labelUsername, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        jPanel.add(textUsername, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        jPanel.add(labelPassword, constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        jPanel.add(fieldPassword, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.CENTER;
        jPanel.add(buttonLogin, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 5;
        constraints.gridheight = 5;
        constraints.anchor = GridBagConstraints.CENTER;
        jPanel.add(imagePanel, constraints);

        // set border for the panel
        jPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Full SinglePlateTracker"));

        this.add(jPanel);
        this.pack();
        this.setLocationRelativeTo(null);   // centers the canvas
    }

    public void main() throws IOException {
        setVisible(true);
            while (true) {
                imagePanel.updateImage(makeTestImage());
                this.repaint();

            }
    }
}
