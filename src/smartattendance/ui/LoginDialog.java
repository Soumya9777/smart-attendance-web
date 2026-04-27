package smartattendance.ui;

import smartattendance.model.Admin;
import smartattendance.model.Teacher;
import smartattendance.store.AttendanceStore;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class LoginDialog extends JDialog {
    private final AttendanceStore store;
    private final String type;
    private boolean authenticated = false;
    private Object user = null;

    public LoginDialog(Frame owner, AttendanceStore store, String type) {
        super(owner, type + " Login", true);
        this.store = store;
        this.type = type;
        
        setSize(350, 400);
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());
        getContentPane().setBackground(new Color(17, 24, 39));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel(type + " Login", SwingConstants.CENTER);
        title.setFont(new Font("Inter", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        JLabel userLabel = new JLabel("ID:");
        userLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 1; gbc.gridwidth = 1;
        add(userLabel, gbc);

        JTextField userField = new JTextField();
        gbc.gridx = 1;
        add(userField, gbc);

        JLabel passLabel = new JLabel("Password:");
        passLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridx = 0; gbc.gridy = 2;
        add(passLabel, gbc);

        JPasswordField passField = new JPasswordField();
        gbc.gridx = 1;
        add(passField, gbc);

        JButton loginBtn = new JButton("Login");
        loginBtn.setBackground(new Color(79, 70, 229));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setOpaque(true);
        loginBtn.setBorderPainted(false);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.insets = new Insets(30, 10, 10, 10);
        add(loginBtn, gbc);

        loginBtn.addActionListener(e -> {
            String id = userField.getText();
            String pass = new String(passField.getPassword());
            try {
                if ("ADMIN".equals(type)) {
                    Optional<Admin> admin = store.findAdminById(id);
                    if (admin.isPresent() && admin.get().passwordMatches(pass)) {
                        authenticated = true;
                        user = admin.get();
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this, "Invalid Admin Credentials");
                    }
                } else {
                    Optional<Teacher> teacher = store.findTeacherById(id);
                    if (teacher.isPresent() && teacher.get().passwordMatches(pass)) {
                        authenticated = true;
                        user = teacher.get();
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(this, "Invalid Teacher Credentials");
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });
    }

    public boolean isAuthenticated() { return authenticated; }
    public Object getUser() { return user; }
}
