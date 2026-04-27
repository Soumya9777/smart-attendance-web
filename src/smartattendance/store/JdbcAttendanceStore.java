package smartattendance.store;

import smartattendance.model.AttendanceRecord;
import smartattendance.model.Admin;
import smartattendance.model.ClassSession;
import smartattendance.model.Student;
import smartattendance.model.Teacher;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAttendanceStore implements AttendanceStore {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public JdbcAttendanceStore(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void initialize() throws IOException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS students ("
                    + "id VARCHAR(30) PRIMARY KEY, name VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS admins ("
                    + "id VARCHAR(30) PRIMARY KEY, name VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS teachers ("
                    + "id VARCHAR(30) PRIMARY KEY, name VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS subjects (subject VARCHAR(100) PRIMARY KEY)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS class_sessions ("
                    + "subject VARCHAR(100) NOT NULL, attendance_date VARCHAR(20) NOT NULL, "
                    + "class_name VARCHAR(100), start_time VARCHAR(10), end_time VARCHAR(10), "
                    + "duration_minutes INTEGER, topic VARCHAR(200), "
                    + "PRIMARY KEY (subject, attendance_date, start_time, end_time))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS attendance ("
                    + "student_id VARCHAR(30) NOT NULL, student_name VARCHAR(100) NOT NULL, "
                    + "subject VARCHAR(100) NOT NULL, "
                    + "attendance_date VARCHAR(20) NOT NULL, start_time VARCHAR(10), end_time VARCHAR(10), "
                    + "marked_at VARCHAR(40) NOT NULL, "
                    + "PRIMARY KEY (student_id, subject, attendance_date, start_time, end_time))");
            
            // Check if default admin exists
            try (PreparedStatement check = connection.prepareStatement("SELECT id FROM admins WHERE id = 'ADM001'")) {
                if (!check.executeQuery().next()) {
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO admins(id, name, password) VALUES ('ADM001', 'Soumya', 'soumyar@njan')")) {
                        insert.executeUpdate();
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Could not initialize JDBC database", exception);
        }
    }

    @Override
    public List<Student> findAllStudents() throws IOException {
        List<Student> students = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, password FROM students ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                students.add(new Student(resultSet.getString("id"), resultSet.getString("name"),
                        resultSet.getString("password")));
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read students", exception);
        }
        return students;
    }

    @Override
    public Optional<Student> findStudentById(String studentId) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, password FROM students WHERE UPPER(id) = UPPER(?)")) {
            statement.setString(1, studentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new Student(resultSet.getString("id"), resultSet.getString("name"),
                            resultSet.getString("password")));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IOException("Could not find student", exception);
        }
    }

    @Override
    public List<Teacher> findAllTeachers() throws IOException {
        List<Teacher> teachers = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, password FROM teachers ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                teachers.add(new Teacher(resultSet.getString("id"), resultSet.getString("name"),
                        resultSet.getString("password")));
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read teachers", exception);
        }
        return teachers;
    }

    @Override
    public Optional<Teacher> findTeacherById(String teacherId) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, password FROM teachers WHERE UPPER(id) = UPPER(?)")) {
            statement.setString(1, teacherId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new Teacher(resultSet.getString("id"), resultSet.getString("name"),
                            resultSet.getString("password")));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IOException("Could not find teacher", exception);
        }
    }

    @Override
    public Optional<Admin> findAdminById(String adminId) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, password FROM admins WHERE UPPER(id) = UPPER(?)")) {
            statement.setString(1, adminId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new Admin(resultSet.getString("id"), resultSet.getString("name"),
                            resultSet.getString("password")));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IOException("Could not find admin", exception);
        }
    }

    @Override
    public List<String> findAllSubjects() throws IOException {
        List<String> subjects = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT subject FROM subjects ORDER BY subject");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                subjects.add(resultSet.getString("subject"));
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read subjects", exception);
        }
        return subjects;
    }

    @Override
    public void saveSubject(String subject) throws IOException {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be empty");
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO subjects(subject) VALUES (?)")) {
            statement.setString(1, subject.trim());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (!exception.getMessage().toLowerCase().contains("duplicate")) {
                throw new IOException("Could not save subject", exception);
            }
        }
    }

    @Override
    public List<String> findSubjectsForStudent(String studentId) throws IOException {
        List<String> subjects = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT DISTINCT subject FROM attendance WHERE UPPER(student_id) = UPPER(?) ORDER BY subject")) {
            statement.setString(1, studentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    subjects.add(resultSet.getString("subject"));
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read student subjects", exception);
        }
        return subjects;
    }

    @Override
    public void saveStudent(Student student) throws IOException {
        String sql = "REPLACE INTO students (id, name, password) VALUES (?, ?, ?)";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.getId());
            statement.setString(2, student.getName());
            statement.setString(3, student.getPassword());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("Could not save student", exception);
        }
    }

    @Override
    public void saveTeacher(Teacher teacher) throws IOException {
        String sql = "REPLACE INTO teachers (id, name, password) VALUES (?, ?, ?)";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacher.getId());
            statement.setString(2, teacher.getName());
            statement.setString(3, teacher.getPassword());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("Could not save teacher", exception);
        }
    }

    @Override
    public void deleteStudent(String studentId) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM students WHERE UPPER(id) = UPPER(?)")) {
            statement.setString(1, studentId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("Could not delete student", exception);
        }
    }

    @Override
    public void deleteTeacher(String teacherId) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM teachers WHERE UPPER(id) = UPPER(?)")) {
            statement.setString(1, teacherId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("Could not delete teacher", exception);
        }
    }

    @Override
    public void recordClassSession(String subject, LocalDate date, String className,
                                   LocalTime startTime, LocalTime endTime, String topic) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO class_sessions(subject, attendance_date, class_name, start_time, end_time, duration_minutes, topic) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, subject);
            statement.setString(2, date.toString());
            statement.setString(3, className);
            statement.setString(4, startTime.toString());
            statement.setString(5, endTime.toString());
            statement.setInt(6, (int) java.time.Duration.between(startTime, endTime).toMinutes());
            statement.setString(7, topic == null || topic.isBlank() ? "Not specified" : topic.trim());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (!exception.getMessage().toLowerCase().contains("duplicate")) {
                throw new IOException("Could not record class session", exception);
            }
        }
    }

    @Override
    public boolean markAttendance(Student student, String subject, LocalDate date,
                                  LocalTime startTime, LocalTime endTime) throws IOException {
        if (findAttendanceByDate(date, subject).stream()
                .anyMatch(record -> record.getStudentId().equalsIgnoreCase(student.getId())
                        && record.getStartTime().equals(startTime)
                        && record.getEndTime().equals(endTime))) {
            return false;
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO attendance(student_id, student_name, subject, attendance_date, start_time, end_time, marked_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, student.getId());
            statement.setString(2, student.getName());
            statement.setString(3, subject);
            statement.setString(4, date.toString());
            statement.setString(5, startTime.toString());
            statement.setString(6, endTime.toString());
            statement.setString(7, LocalDateTime.now().toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            throw new IOException("Could not mark attendance", exception);
        }
    }

    @Override
    public boolean removeAttendance(String studentId, String subject, LocalDate date,
                                    LocalTime startTime, LocalTime endTime) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM attendance WHERE UPPER(student_id) = UPPER(?) "
                             + "AND UPPER(subject) = UPPER(?) AND attendance_date = ? "
                             + "AND start_time = ? AND end_time = ?")) {
            statement.setString(1, studentId);
            statement.setString(2, subject);
            statement.setString(3, date.toString());
            statement.setString(4, startTime.toString());
            statement.setString(5, endTime.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IOException("Could not remove attendance", exception);
        }
    }

    @Override
    public List<AttendanceRecord> findAttendanceByDate(LocalDate date, String subject) throws IOException {
        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT student_id, student_name, subject, attendance_date, start_time, end_time, marked_at FROM attendance "
                             + "WHERE attendance_date = ? AND UPPER(subject) = UPPER(?) ORDER BY marked_at")) {
            statement.setString(1, date.toString());
            statement.setString(2, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new AttendanceRecord(resultSet.getString("student_id"),
                            resultSet.getString("student_name"),
                            resultSet.getString("subject"),
                            LocalDate.parse(resultSet.getString("attendance_date")),
                            LocalTime.parse(resultSet.getString("start_time")),
                            LocalTime.parse(resultSet.getString("end_time")),
                            LocalDateTime.parse(resultSet.getString("marked_at"))));
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read attendance", exception);
        }
        return records;
    }

    @Override
    public int countClassSessions(String subject) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM class_sessions WHERE UPPER(subject) = UPPER(?)")) {
            statement.setString(1, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IOException("Could not count class sessions", exception);
        }
    }

    @Override
    public int countStudentAttendance(String studentId, String subject) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM attendance WHERE UPPER(student_id) = UPPER(?) AND UPPER(subject) = UPPER(?)")) {
            statement.setString(1, studentId);
            statement.setString(2, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IOException("Could not count student attendance", exception);
        }
    }

    @Override
    public List<ClassSession> findClassSessions(String subject) throws IOException {
        List<ClassSession> sessions = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT subject, attendance_date, class_name, start_time, end_time, topic FROM class_sessions "
                             + "WHERE UPPER(subject) = UPPER(?) ORDER BY attendance_date, start_time")) {
            statement.setString(1, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessions.add(new ClassSession(resultSet.getString("subject"),
                            LocalDate.parse(resultSet.getString("attendance_date")),
                            resultSet.getString("class_name"),
                            LocalTime.parse(resultSet.getString("start_time")),
                            LocalTime.parse(resultSet.getString("end_time")),
                            resultSet.getString("topic")));
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read class sessions", exception);
        }
        return sessions;
    }

    @Override
    public List<AttendanceRecord> findStudentAttendanceRecords(String studentId, String subject) throws IOException {
        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT student_id, student_name, subject, attendance_date, start_time, end_time, marked_at "
                             + "FROM attendance WHERE UPPER(student_id) = UPPER(?) "
                             + "AND UPPER(subject) = UPPER(?) ORDER BY attendance_date, start_time")) {
            statement.setString(1, studentId);
            statement.setString(2, subject);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new AttendanceRecord(resultSet.getString("student_id"),
                            resultSet.getString("student_name"),
                            resultSet.getString("subject"),
                            LocalDate.parse(resultSet.getString("attendance_date")),
                            LocalTime.parse(resultSet.getString("start_time")),
                            LocalTime.parse(resultSet.getString("end_time")),
                            LocalDateTime.parse(resultSet.getString("marked_at"))));
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Could not read student attendance records", exception);
        }
        return records;
    }

    @Override
    public void deleteClassSession(String subject, LocalDate date, LocalTime startTime) throws IOException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                // Delete attendance records first
                try (PreparedStatement st = connection.prepareStatement(
                        "DELETE FROM attendance WHERE UPPER(subject) = UPPER(?) AND attendance_date = ? AND start_time = ?")) {
                    st.setString(1, subject);
                    st.setString(2, date.toString());
                    st.setString(3, startTime.toString());
                    st.executeUpdate();
                }
                // Delete session record
                try (PreparedStatement st = connection.prepareStatement(
                        "DELETE FROM class_sessions WHERE UPPER(subject) = UPPER(?) AND attendance_date = ? AND start_time = ?")) {
                    st.setString(1, subject);
                    st.setString(2, date.toString());
                    st.setString(3, startTime.toString());
                    st.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException exception) {
            throw new IOException("Could not delete class session", exception);
        }
    }

    private Connection connect() throws SQLException {
        if (username == null || username.isBlank()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
