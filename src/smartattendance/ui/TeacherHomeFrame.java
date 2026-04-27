package smartattendance.ui;

import smartattendance.qr.QrCodeGenerator;
import smartattendance.web.TokenService;

import javax.swing.*;
import java.awt.*;

public class TeacherHomeFrame extends JFrame {
    private final TokenService tokenService;
    private final JLabel qrLabel;
    private final JLabel statusLabel;
    private final JLabel infoLabel;

    public TeacherHomeFrame(TokenService tokenService) {
        this.tokenService = tokenService;
        
        setTitle("NIST Smart Attendance - Teacher Dashboard");
        setSize(500, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(20, 20));
        getContentPane().setBackground(new Color(17, 24, 39)); // Dark background

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(31, 41, 55));
        header.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("Teacher Attendance Portal", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel);

        infoLabel = new JLabel("No Active Session", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Inter", Font.PLAIN, 16));
        infoLabel.setForeground(new Color(156, 163, 175));
        header.add(infoLabel);
        
        add(header, BorderLayout.NORTH);

        // QR Area
        JPanel qrPanel = new JPanel(new GridBagLayout());
        qrPanel.setBackground(new Color(17, 24, 39));
        
        qrLabel = new JLabel();
        qrLabel.setPreferredSize(new Dimension(350, 350));
        qrLabel.setOpaque(true);
        qrLabel.setBackground(Color.WHITE);
        qrLabel.setBorder(BorderFactory.createLineBorder(new Color(79, 70, 229), 4));
        qrPanel.add(qrLabel);
        
        add(qrPanel, BorderLayout.CENTER);

        // Footer / Status
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(31, 41, 55));
        footer.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        statusLabel = new JLabel("Waiting for session to start on Web Portal...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Inter", Font.BOLD, 14));
        statusLabel.setForeground(new Color(239, 68, 68)); // Red
        footer.add(statusLabel, BorderLayout.CENTER);

        add(footer, BorderLayout.SOUTH);

        // Timer to update UI
        Timer timer = new Timer(500, e -> updateUI());
        timer.start();
    }

    private void updateUI() {
        if (tokenService.isSessionActive()) {
            infoLabel.setText(tokenService.getActiveSubject() + " | " + tokenService.getActiveClassName());
            statusLabel.setText("● SESSION ACTIVE - QR ROTATING (1s)");
            statusLabel.setForeground(new Color(52, 211, 153)); // Green
            
            TokenService.AttendanceToken token = tokenService.getCurrentToken();
            if (token != null) {
                // Generate QR URL matching the web version
                String qrUrl = "/scan?token=" + token.getValue() + "&subject=" + tokenService.getActiveSubject();
                ImageIcon icon = new ImageIcon(QrCodeGenerator.createQrImage(qrUrl, 10, 2));
                qrLabel.setIcon(icon);
            }
        } else {
            infoLabel.setText("No Active Session");
            statusLabel.setText("Please start a session from the browser first.");
            statusLabel.setForeground(new Color(239, 68, 68));
            qrLabel.setIcon(null);
            qrLabel.setText("Start Session on Web");
            qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        }
    }
}
