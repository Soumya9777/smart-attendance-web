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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
    private final String teacherName;
    private final Runnable onLogout;
    
    private final JLabel qrLabel;
    private final JLabel statusLabel;
    private final JLabel infoLabel;
    private final JTabbedPane tabs;

    public TeacherHomeFrame(TokenService tokenService, AttendanceStore store, String serverUrl, String teacherName, Runnable onLogout) {
        this.tokenService = tokenService;
        this.store = store;
        this.serverUrl = serverUrl;
        this.teacherName = teacherName;
        this.onLogout = onLogout;
        
        setTitle("Teacher Dashboard - " + teacherName);
        setSize(850, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top Bar with Name and Logout
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(31, 41, 55));
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        
        JLabel title = new JLabel("Welcome, " + teacherName, SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Inter", Font.BOLD, 18));
        topBar.add(title, BorderLayout.WEST);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> {
            dispose();
            if (onLogout != null) onLogout.run();
        });
        topBar.add(logoutBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.setFont(new Font("Inter", Font.PLAIN, 14));
        
        // Active Session Tab
        JPanel activePanel = new JPanel(new BorderLayout(10, 10));
        activePanel.setBackground(new Color(243, 244, 246));
        activePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        infoLabel = new JLabel("Ready to start session", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Inter", Font.BOLD, 18));
        activePanel.add(infoLabel, BorderLayout.NORTH);

        qrLabel = new JLabel("", SwingConstants.CENTER);
        qrLabel.setOpaque(true);
        qrLabel.setBackground(Color.WHITE);
        qrLabel.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 1));
        activePanel.add(qrLabel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton startBtn = new JButton("Start New Session");
        JButton stopBtn = new JButton("Stop & Save");
        JButton copyBtn = new JButton("Copy Link");
        
        startBtn.addActionListener(e -> startSessionDialog());
        stopBtn.addActionListener(e -> stopSession());
        copyBtn.addActionListener(e -> copyLink());
        
        controls.add(startBtn);
        controls.add(stopBtn);
        controls.add(copyBtn);
        
        statusLabel = new JLabel("Local Server: " + serverUrl, SwingConstants.CENTER);
        statusLabel.setForeground(new Color(107, 114, 128));
        
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        bottomPanel.setOpaque(false);
        bottomPanel.add(controls);
        bottomPanel.add(statusLabel);
        activePanel.add(bottomPanel, BorderLayout.SOUTH);

        tabs.addTab("Active QR", activePanel);
        
        // History Tab
        tabs.addTab("Attendance Records", createHistoryPanel());
        
        add(tabs, BorderLayout.CENTER);

        Timer timer = new Timer(1000, e -> updateUI());
        timer.start();
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);

        DefaultTableModel sessionModel = new DefaultTableModel(new String[]{"Date", "Subject", "Class", "Time", "Topic"}, 0);
        JTable sessionTable = new JTable(sessionModel);
        sessionTable.setRowHeight(30);
        sessionTable.getTableHeader().setFont(new Font("Inter", Font.BOLD, 12));
        
        DefaultTableModel recordModel = new DefaultTableModel(new String[]{"Student ID", "Name", "Marked Time"}, 0);
        JTable recordTable = new JTable(recordModel);
        recordTable.setRowHeight(25);

        JButton refreshBtn = new JButton("🔄 Refresh List");
        refreshBtn.addActionListener(e -> refreshHistory(sessionModel));

        JButton deleteRecordBtn = new JButton("Delete Record");
        deleteRecordBtn.setForeground(new Color(220, 38, 38));
        deleteRecordBtn.addActionListener(e -> deleteSelectedRecord(sessionTable, sessionModel, recordTable, recordModel));

        JButton deleteSessionBtn = new JButton("Delete Whole Session");
        deleteSessionBtn.setForeground(new Color(220, 38, 38));
        deleteSessionBtn.addActionListener(e -> deleteSelectedSession(sessionTable, sessionModel, recordModel));

        JButton exportBtn = new JButton("📄 Export to PDF (HTML Report)");
        exportBtn.setBackground(new Color(5, 150, 105));
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setOpaque(true);
        exportBtn.setBorderPainted(false);
        exportBtn.addActionListener(e -> exportToPdf(sessionTable, sessionModel));

        sessionTable.getSelectionModel().addListSelectionListener(e -> showRecords(sessionTable, sessionModel, recordModel));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(sessionTable), new JScrollPane(recordTable));
        split.setDividerLocation(250);
        split.setBorder(null);
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(new JLabel("Class Sessions", SwingConstants.LEFT), BorderLayout.WEST);
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footerActions.setOpaque(false);
        footerActions.add(deleteRecordBtn);
        footerActions.add(deleteSessionBtn);
        footerActions.add(exportBtn);
        panel.add(footerActions, BorderLayout.SOUTH);
        
        refreshHistory(sessionModel);
        return panel;
    }

    private void exportToPdf(JTable table, DefaultTableModel sessionModel) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a session from the list first!");
            return;
        }

        try {
            String subject = (String) sessionModel.getValueAt(row, 1);
            String className = (String) sessionModel.getValueAt(row, 2);
            String dateStr = (String) sessionModel.getValueAt(row, 0).toString();
            String timeStr = (String) sessionModel.getValueAt(row, 3);
            String topic = (String) sessionModel.getValueAt(row, 4);

            List<AttendanceRecord> records = store.findAttendanceByDate(LocalDate.parse(dateStr), subject);
            LocalTime sessionStart = LocalTime.parse(timeStr.split("-")[0]);

            File file = new File("Attendance_Report_" + subject + "_" + dateStr + ".html");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write("<!DOCTYPE html><html><head><title>Attendance Report</title>");
                writer.write("<style>body{font-family:sans-serif;padding:40px;color:#333} h1{color:#1f2937} .meta{margin-bottom:30px;color:#666}");
                writer.write("table{width:100%;border-collapse:collapse} th,td{border:1px solid #ddd;padding:12px;text-align:left} th{background:#f9fafb}</style></head><body>");
                writer.write("<h1>Attendance Report</h1>");
                writer.write("<div class='meta'>");
                writer.write("<p><b>Teacher:</b> " + teacherName + "</p>");
                writer.write("<p><b>Subject:</b> " + subject + " | <b>Class:</b> " + className + "</p>");
                writer.write("<p><b>Topic:</b> " + topic + "</p>");
                writer.write("<p><b>Date:</b> " + dateStr + " | <b>Time:</b> " + timeStr + "</p>");
                writer.write("</div>");
                writer.write("<table><thead><tr><th>Student ID</th><th>Student Name</th><th>Marked At</th></tr></thead><tbody>");
                
                int count = 0;
                for (AttendanceRecord r : records) {
                    if (r.getStartTime().equals(sessionStart)) {
                        writer.write("<tr><td>" + r.getStudentId() + "</td><td>" + r.getStudentName() + "</td><td>" + r.getMarkedAt().toLocalTime() + "</td></tr>");
                        count++;
                    }
                }
                
                writer.write("</tbody></table>");
                writer.write("<p style='margin-top:20px'><b>Total Students Present:</b> " + count + "</p>");
                writer.write("</body></html>");
            }

            JOptionPane.showMessageDialog(this, "Report Generated: " + file.getName() + "\nOpening in browser... You can save it as PDF from there!");
            Desktop.getDesktop().browse(file.toURI());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error exporting: " + ex.getMessage());
        }
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
            infoLabel.setText("Ready to start session");
            statusLabel.setText("Local Server: " + serverUrl);
            qrLabel.setIcon(null);
        }
    }
}
