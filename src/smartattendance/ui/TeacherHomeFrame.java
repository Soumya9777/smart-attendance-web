package smartattendance.ui;

import smartattendance.qr.QrCodeGenerator;
import smartattendance.web.TokenService;
import smartattendance.store.AttendanceStore;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;

public class TeacherHomeFrame extends JFrame {
    private final TokenService tokenService;
    private final AttendanceStore store;
    private final String serverUrl;
    private final JLabel qrLabel;
    private final JLabel statusLabel;
    private final JLabel infoLabel;

    public TeacherHomeFrame(TokenService tokenService, AttendanceStore store, String serverUrl) {
        this.tokenService = tokenService;
        this.store = store;
        this.serverUrl = serverUrl;
        
        setTitle("Teacher Dashboard");
        setSize(550, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(243, 244, 246));

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(79, 70, 229));
        JLabel title = new JLabel("Smart Attendance - Teacher", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Inter", Font.BOLD, 20));
        header.add(title);

        infoLabel = new JLabel("No Session Active", SwingConstants.CENTER);
        infoLabel.setForeground(Color.WHITE);
        header.add(infoLabel);
        add(header, BorderLayout.NORTH);

        // QR Center
        qrLabel = new JLabel("START SESSION", SwingConstants.CENTER);
        qrLabel.setPreferredSize(new Dimension(300, 300));
        qrLabel.setOpaque(true);
        qrLabel.setBackground(Color.WHITE);
        qrLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        add(qrLabel, BorderLayout.CENTER);

        // Control Panel
        JPanel controls = new JPanel(new GridLayout(3, 1, 5, 5));
        JButton startBtn = new JButton("Start Session");
        JButton stopBtn = new JButton("Stop Session");
        JButton copyBtn = new JButton("Copy Student Link");

        startBtn.addActionListener(e -> startSessionDialog());
        stopBtn.addActionListener(e -> {
            if (tokenService.isSessionActive()) {
                try {
                    store.recordClassSession(tokenService.getActiveSubject(), java.time.LocalDate.now(), 
                        tokenService.getActiveClassName(), tokenService.getActiveStartTime(), 
                        tokenService.getActiveEndTime(), tokenService.getActiveTopic());
                    tokenService.stopSession();
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });
        
        copyBtn.addActionListener(e -> {
            StringSelection selection = new StringSelection(serverUrl);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            JOptionPane.showMessageDialog(this, "Link Copied: " + serverUrl);
        });

        JPanel btnGroup = new JPanel();
        btnGroup.add(startBtn);
        btnGroup.add(stopBtn);
        btnGroup.add(copyBtn);
        controls.add(btnGroup);

        statusLabel = new JLabel("Offline", SwingConstants.CENTER);
        controls.add(statusLabel);
        add(controls, BorderLayout.SOUTH);

        Timer timer = new Timer(1000, e -> updateUI());
        timer.start();
    }

    private void startSessionDialog() {
        try {
            String className = JOptionPane.showInputDialog("Class Name:");
            String[] subjects = store.findAllSubjects().toArray(new String[0]);
            if (subjects.length == 0) {
                JOptionPane.showMessageDialog(this, "No subjects found. Ask Admin to add subjects.");
                return;
            }
            String subject = (String) JOptionPane.showInputDialog(this, "Select Subject:", "Subject", 
                JOptionPane.QUESTION_MESSAGE, null, subjects, subjects[0]);
            String topic = JOptionPane.showInputDialog("Topic:");

            if (className != null && subject != null) {
                tokenService.startSession(className, subject, LocalTime.now(), LocalTime.now().plusHours(1), topic);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateUI() {
        if (tokenService.isSessionActive()) {
            infoLabel.setText(tokenService.getActiveSubject() + " (" + tokenService.getActiveClassName() + ")");
            statusLabel.setText("Active (Rotating 1s)");
            
            String qrUrl = serverUrl + "/scan?token=" + tokenService.getCurrentToken().getValue() + "&subject=" + tokenService.getActiveSubject();
            qrLabel.setIcon(new ImageIcon(QrCodeGenerator.createQrImage(qrUrl, 10, 1)));
        } else {
            infoLabel.setText("No Session Active");
            statusLabel.setText("Ready");
            qrLabel.setIcon(null);
            qrLabel.setText("START SESSION");
        }
    }
}
