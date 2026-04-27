package smartattendance.ui;

import smartattendance.model.AttendanceRecord;
import smartattendance.model.ClassSession;
import smartattendance.qr.QrCodeGenerator;
import smartattendance.web.TokenService;
import smartattendance.store.AttendanceStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TeacherHomeFrame extends JFrame {
    private final TokenService tokenService;
    private final AttendanceStore store;
    private final String serverUrl;
    private final Runnable onLogout;
    
    private final JLabel qrLabel;
    private final JLabel statusLabel;
    private final JLabel infoLabel;
    private final JTabbedPane tabs;

    public TeacherHomeFrame(TokenService tokenService, AttendanceStore store, String serverUrl, Runnable onLogout) {
        this.tokenService = tokenService;
        this.store = store;
        this.serverUrl = serverUrl;
        this.onLogout = onLogout;
        
        setTitle("Teacher Dashboard");
        setSize(800, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top Bar with Logout
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(79, 70, 229));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        JLabel title = new JLabel("Teacher Portal", SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Inter", Font.BOLD, 18));
        topBar.add(title, BorderLayout.WEST);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> {
            dispose();
            if (onLogout != null) onLogout.run();
        });
        topBar.add(logoutBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        tabs = new JTabbedPane();
        
        // Active Session Tab
        JPanel activePanel = new JPanel(new BorderLayout(10, 10));
        activePanel.setBackground(new Color(243, 244, 246));

        infoLabel = new JLabel("No Session Active", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Inter", Font.BOLD, 16));
        activePanel.add(infoLabel, BorderLayout.NORTH);

        qrLabel = new JLabel("START SESSION", SwingConstants.CENTER);
        qrLabel.setPreferredSize(new Dimension(300, 300));
        qrLabel.setOpaque(true);
        qrLabel.setBackground(Color.WHITE);
        qrLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        activePanel.add(qrLabel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout());
        JButton startBtn = new JButton("Start Session");
        JButton stopBtn = new JButton("Stop Session");
        JButton copyBtn = new JButton("Copy Link");
        
        startBtn.addActionListener(e -> startSessionDialog());
        stopBtn.addActionListener(e -> stopSession());
        copyBtn.addActionListener(e -> copyLink());
        
        controls.add(startBtn);
        controls.add(stopBtn);
        controls.add(copyBtn);
        
        statusLabel = new JLabel("Ready", SwingConstants.CENTER);
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1));
        bottomPanel.add(controls);
        bottomPanel.add(statusLabel);
        activePanel.add(bottomPanel, BorderLayout.SOUTH);

        tabs.addTab("Active Session", activePanel);
        
        // History Tab
        tabs.addTab("Attendance History", createHistoryPanel());
        
        add(tabs, BorderLayout.CENTER);

        Timer timer = new Timer(1000, e -> updateUI());
        timer.start();
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultTableModel sessionModel = new DefaultTableModel(new String[]{"Date", "Subject", "Class", "Time", "Topic"}, 0);
        JTable sessionTable = new JTable(sessionModel);
        
        DefaultTableModel recordModel = new DefaultTableModel(new String[]{"Student ID", "Name", "Time"}, 0);
        JTable recordTable = new JTable(recordModel);

        JButton refreshBtn = new JButton("Refresh History");
        refreshBtn.addActionListener(e -> refreshHistory(sessionModel));

        JButton deleteBtn = new JButton("Delete Attendance Record");
        deleteBtn.setForeground(Color.RED);
        deleteBtn.addActionListener(e -> {
            int sessionRow = sessionTable.getSelectedRow();
            int recordRow = recordTable.getSelectedRow();
            if (sessionRow >= 0 && recordRow >= 0) {
                String studentId = (String) recordModel.getValueAt(recordRow, 0);
                String subject = (String) sessionModel.getValueAt(sessionRow, 1);
                LocalDate date = LocalDate.parse(sessionModel.getValueAt(sessionRow, 0).toString());
                String[] times = sessionModel.getValueAt(sessionRow, 3).toString().split("-");
                LocalTime start = LocalTime.parse(times[0]);
                LocalTime end = LocalTime.parse(times[1]);
                
                int confirm = JOptionPane.showConfirmDialog(this, "Remove attendance for " + studentId + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        store.removeAttendance(studentId, subject, date, start, end);
                        showRecords(sessionTable, sessionModel, recordModel);
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
            }
        });

        sessionTable.getSelectionModel().addListSelectionListener(e -> showRecords(sessionTable, sessionModel, recordModel));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(sessionTable), new JScrollPane(recordTable));
        split.setDividerLocation(200);
        
        panel.add(refreshBtn, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(deleteBtn, BorderLayout.SOUTH);
        
        refreshHistory(sessionModel);
        return panel;
    }

    private void refreshHistory(DefaultTableModel model) {
        model.setRowCount(0);
        try {
            for (String subject : store.findAllSubjects()) {
                for (ClassSession s : store.findClassSessions(subject)) {
                    model.addRow(new Object[]{s.getDate(), subject, s.getClassName(), s.getStartTime() + "-" + s.getEndTime(), s.getTopic()});
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showRecords(JTable table, DefaultTableModel sessionModel, DefaultTableModel recordModel) {
        recordModel.setRowCount(0);
        int row = table.getSelectedRow();
        if (row >= 0) {
            try {
                LocalDate date = LocalDate.parse(sessionModel.getValueAt(row, 0).toString());
                String subject = (String) sessionModel.getValueAt(row, 1);
                String[] times = sessionModel.getValueAt(row, 3).toString().split("-");
                LocalTime start = LocalTime.parse(times[0]);
                
                List<AttendanceRecord> records = store.findAttendanceByDate(date, subject);
                for (AttendanceRecord r : records) {
                    if (r.getStartTime().equals(start)) {
                        recordModel.addRow(new Object[]{r.getStudentId(), r.getStudentName(), r.getMarkedAt().toLocalTime()});
                    }
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void startSessionDialog() {
        try {
            String className = JOptionPane.showInputDialog("Class Name (e.g. CS-2024):");
            String[] subjects = store.findAllSubjects().toArray(new String[0]);
            if (subjects.length == 0) return;
            String subject = (String) JOptionPane.showInputDialog(this, "Select Subject:", "Subject", 
                JOptionPane.QUESTION_MESSAGE, null, subjects, subjects[0]);
            String topic = JOptionPane.showInputDialog("Topic:");

            if (className != null && subject != null) {
                tokenService.startSession(className, subject, LocalTime.now(), LocalTime.now().plusHours(1), topic);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopSession() {
        if (tokenService.isSessionActive()) {
            try {
                store.recordClassSession(tokenService.getActiveSubject(), LocalDate.now(), 
                    tokenService.getActiveClassName(), tokenService.getActiveStartTime(), 
                    tokenService.getActiveEndTime(), tokenService.getActiveTopic());
                tokenService.stopSession();
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void copyLink() {
        StringSelection selection = new StringSelection(serverUrl);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        JOptionPane.showMessageDialog(this, "Student Portal Link Copied!");
    }

    private void updateUI() {
        if (tokenService.isSessionActive()) {
            infoLabel.setText(tokenService.getActiveSubject() + " (" + tokenService.getActiveClassName() + ")");
            statusLabel.setText("Active (1s QR Rotation)");
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
