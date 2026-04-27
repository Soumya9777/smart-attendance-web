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
import java.time.ZoneId;
import java.util.Date;
import java.util.Calendar;
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
        setSize(800, 800);
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

        JButton deleteRecordBtn = new JButton("Delete Attendance Record");
        deleteRecordBtn.setForeground(Color.RED);
        deleteRecordBtn.addActionListener(e -> deleteSelectedRecord(sessionTable, sessionModel, recordTable, recordModel));

        JButton deleteSessionBtn = new JButton("Delete Whole Session");
        deleteSessionBtn.setForeground(Color.RED);
        deleteSessionBtn.setFont(new Font("Inter", Font.BOLD, 12));
        deleteSessionBtn.addActionListener(e -> deleteSelectedSession(sessionTable, sessionModel, recordModel));

        sessionTable.getSelectionModel().addListSelectionListener(e -> showRecords(sessionTable, sessionModel, recordModel));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(sessionTable), new JScrollPane(recordTable));
        split.setDividerLocation(200);
        
        panel.add(refreshBtn, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footerActions.add(deleteRecordBtn);
        footerActions.add(deleteSessionBtn);
        panel.add(footerActions, BorderLayout.SOUTH);
        
        refreshHistory(sessionModel);
        return panel;
    }

    private void deleteSelectedSession(JTable table, DefaultTableModel sessionModel, DefaultTableModel recordModel) {
        int row = table.getSelectedRow();
        if (row >= 0) {
            String subject = (String) sessionModel.getValueAt(row, 1);
            LocalDate date = LocalDate.parse(sessionModel.getValueAt(row, 0).toString());
            String startTimeStr = sessionModel.getValueAt(row, 3).toString().split("-")[0];
            LocalTime start = LocalTime.parse(startTimeStr);

            int confirm = JOptionPane.showConfirmDialog(this, "DELETE ENTIRE SESSION for " + subject + "?\nAll attendance records will be lost!", "Confirm Delete Session", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    store.deleteClassSession(subject, date, start);
                    refreshHistory(sessionModel);
                    recordModel.setRowCount(0);
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error deleting session: " + ex.getMessage()); }
            }
        }
    }

    private void deleteSelectedRecord(JTable sessionTable, DefaultTableModel sessionModel, JTable recordTable, DefaultTableModel recordModel) {
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
            String[] subjects = store.findAllSubjects().toArray(new String[0]);
            if (subjects.length == 0) {
                JOptionPane.showMessageDialog(this, "No subjects found. Ask Admin to add subjects.");
                return;
            }

            JComboBox<String> subjectCombo = new JComboBox<>(subjects);
            JTextField classField = new JTextField();
            JTextField topicField = new JTextField();
            
            // Time Spinners
            Calendar cal = Calendar.getInstance();
            Date now = cal.getTime();
            
            SpinnerDateModel startModel = new SpinnerDateModel(now, null, null, Calendar.MINUTE);
            JSpinner startSpinner = new JSpinner(startModel);
            JSpinner.DateEditor startEditor = new JSpinner.DateEditor(startSpinner, "HH:mm");
            startSpinner.setEditor(startEditor);
            
            cal.add(Calendar.HOUR, 1);
            Date hourLater = cal.getTime();
            SpinnerDateModel endModel = new SpinnerDateModel(hourLater, null, null, Calendar.MINUTE);
            JSpinner endSpinner = new JSpinner(endModel);
            JSpinner.DateEditor endEditor = new JSpinner.DateEditor(endSpinner, "HH:mm");
            endSpinner.setEditor(endEditor);

            Object[] message = {
                "Select Subject:", subjectCombo,
                "Class Name:", classField,
                "Topic:", topicField,
                "Start Time:", startSpinner,
                "End Time:", endSpinner
            };

            int option = JOptionPane.showConfirmDialog(this, message, "Start New Session", JOptionPane.OK_CANCEL_OPTION);
            
            if (option == JOptionPane.OK_OPTION) {
                String subject = (String) subjectCombo.getSelectedItem();
                String className = classField.getText();
                String topic = topicField.getText();
                
                LocalTime start = toLocalTime((Date) startSpinner.getValue());
                LocalTime end = toLocalTime((Date) endSpinner.getValue());

                if (subject != null && !className.isBlank()) {
                    tokenService.startSession(className, subject, start, end, topic);
                } else {
                    JOptionPane.showMessageDialog(this, "Subject and Class Name are required!");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private LocalTime toLocalTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
    }

    private void stopSession() {
        if (tokenService.isSessionActive()) {
            try {
                store.recordClassSession(tokenService.getActiveSubject(), LocalDate.now(), 
                    tokenService.getActiveClassName(), tokenService.getActiveStartTime(), 
                    tokenService.getActiveEndTime(), tokenService.getActiveTopic());
                tokenService.stopSession();
                JOptionPane.showMessageDialog(this, "Session stopped and saved.");
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
            statusLabel.setText("Local Server: " + serverUrl);
            qrLabel.setIcon(null);
            qrLabel.setText("START SESSION");
        }
    }
}
