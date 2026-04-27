package smartattendance.ui;

import smartattendance.model.Student;
import smartattendance.model.Teacher;
import smartattendance.store.AttendanceStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class AdminFrame extends JFrame {
    private final AttendanceStore store;

    public AdminFrame(AttendanceStore store) {
        this.store = store;
        setTitle("Admin Management Portal");
        setSize(900, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Students", createStudentPanel());
        tabs.addTab("Teachers", createTeacherPanel());
        tabs.addTab("Subjects", createSubjectPanel());
        
        add(tabs);
    }

    private JPanel createStudentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ID", "Name"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        
        refreshStudents(model);

        JPanel actions = new JPanel();
        JButton addBtn = new JButton("Add Student");
        JButton delBtn = new JButton("Delete Student");
        
        addBtn.addActionListener(e -> {
            JTextField idField = new JTextField();
            JTextField nameField = new JTextField();
            JPasswordField passField = new JPasswordField();
            Object[] message = {
                "Student ID:", idField,
                "Student Name:", nameField,
                "Password:", passField
            };

            int option = JOptionPane.showConfirmDialog(null, message, "Add New Student", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                try {
                    store.saveStudent(new Student(idField.getText(), nameField.getText(), new String(passField.getPassword())));
                    refreshStudents(model);
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
            }
        });

        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                String id = (String) model.getValueAt(row, 0);
                try {
                    store.deleteStudent(id);
                    refreshStudents(model);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        actions.add(addBtn);
        actions.add(delBtn);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createTeacherPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ID", "Name"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        
        refreshTeachers(model);

        JPanel actions = new JPanel();
        JButton addBtn = new JButton("Add Teacher");
        JButton delBtn = new JButton("Delete Teacher");

        addBtn.addActionListener(e -> {
            JTextField idField = new JTextField();
            JTextField nameField = new JTextField();
            JPasswordField passField = new JPasswordField();
            Object[] message = {
                "Teacher ID:", idField,
                "Teacher Name:", nameField,
                "Password:", passField
            };

            int option = JOptionPane.showConfirmDialog(null, message, "Add New Teacher", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                try {
                    store.saveTeacher(new Teacher(idField.getText(), nameField.getText(), new String(passField.getPassword())));
                    refreshTeachers(model);
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
            }
        });

        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                String id = (String) model.getValueAt(row, 0);
                try {
                    store.deleteTeacher(id);
                    refreshTeachers(model);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        actions.add(addBtn);
        actions.add(delBtn);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createSubjectPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        
        refreshSubjects(model);

        JPanel actions = new JPanel();
        JButton addBtn = new JButton("Add Subject");
        addBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog("Enter Subject Name:");
            if (name != null) {
                try {
                    store.saveSubject(name);
                    refreshSubjects(model);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        actions.add(addBtn);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshStudents(DefaultTableModel model) {
        model.setRowCount(0);
        try {
            for (Student s : store.findAllStudents()) {
                model.addRow(new Object[]{s.getId(), s.getName()});
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void refreshTeachers(DefaultTableModel model) {
        model.setRowCount(0);
        try {
            for (Teacher t : store.findAllTeachers()) {
                model.addRow(new Object[]{t.getId(), t.getName()});
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void refreshSubjects(DefaultListModel<String> model) {
        model.clear();
        try {
            for (String s : store.findAllSubjects()) {
                model.addElement(s);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
