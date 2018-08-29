package sproj.util;

import org.bytedeco.javacv.CanvasFrame;

import javax.swing.*;
import java.awt.*;

public class TrackerCanvasFrame extends CanvasFrame {


    public TrackerCanvasFrame(String title) {
        super(title);       // todo  add Gamma / screen number / other constructor args here?
        addComponents();
    }

    private void addComponents() {
        JPanel jPanel = new JPanel(new GridBagLayout());

        JLabel labelUsername = new JLabel("Enter username: ");
        JLabel labelPassword = new JLabel("Enter password: ");
        JTextField textUsername = new JTextField(20);
        JPasswordField fieldPassword = new JPasswordField(20);
        JButton buttonLogin = new JButton("Login");

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 10, 10, 10);

        // add components to the panel
        constraints.gridx = 0;
        constraints.gridy = 0;
        jPanel.add(labelUsername, constraints);

        constraints.gridx = 1;
        jPanel.add(textUsername, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        jPanel.add(labelPassword, constraints);

        constraints.gridx = 1;
        jPanel.add(fieldPassword, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.CENTER;
        jPanel.add(buttonLogin, constraints);

        // set border for the panel
        jPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Login Panel"));

        this.add(jPanel);
        this.pack();
        this.setLocationRelativeTo(null);   // centers the canvas
    }
}
