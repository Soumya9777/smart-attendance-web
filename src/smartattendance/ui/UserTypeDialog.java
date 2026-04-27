package smartattendance.ui;

import javax.swing.*;
import java.awt.*;

public class UserTypeDialog extends JDialog {
    private String userType = null;

    public UserTypeDialog(Frame owner) {
        super(owner, "Select User Type", true);
        setSize(400, 300);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(17, 24, 39));

        JLabel header = new JLabel("Welcome to Smart Attendance", SwingConstants.CENTER);
        header.setFont(new Font("Inter", Font.BOLD, 20));
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(30, 10, 20, 10));
        add(header, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        buttonPanel.setBackground(new Color(17, 24, 39));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 40, 40));

        JButton adminBtn = createStyledButton("Admin", new Color(79, 70, 229));
        JButton teacherBtn = createStyledButton("Teacher", new Color(16, 185, 129));

        adminBtn.addActionListener(e -> { userType = "ADMIN"; dispose(); });
        teacherBtn.addActionListener(e -> { userType = "TEACHER"; dispose(); });

        buttonPanel.add(adminBtn);
        buttonPanel.add(teacherBtn);
        add(buttonPanel, BorderLayout.CENTER);
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Inter", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public String getUserType() {
        return userType;
    }
}
