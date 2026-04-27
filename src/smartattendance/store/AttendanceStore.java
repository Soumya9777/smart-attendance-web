package smartattendance.store;

import smartattendance.model.AttendanceRecord;
import smartattendance.model.Admin;
import smartattendance.model.ClassSession;
import smartattendance.model.Student;
import smartattendance.model.Teacher;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AttendanceStore {
    void initialize() throws IOException;

    List<Student> findAllStudents() throws IOException;

    Optional<Student> findStudentById(String studentId) throws IOException;

    List<Teacher> findAllTeachers() throws IOException;

    Optional<Teacher> findTeacherById(String teacherId) throws IOException;

    Optional<Admin> findAdminById(String adminId) throws IOException;

    List<String> findAllSubjects() throws IOException;

    void saveSubject(String subject) throws IOException;

    List<String> findSubjectsForStudent(String studentId) throws IOException;

    void saveStudent(Student student) throws IOException;

    void saveTeacher(Teacher teacher) throws IOException;

    void deleteStudent(String studentId) throws IOException;

    void deleteTeacher(String teacherId) throws IOException;

    void recordClassSession(String subject, LocalDate date, String className, LocalTime startTime,
                            LocalTime endTime, String topic) throws IOException;

    boolean markAttendance(Student student, String subject, LocalDate date, LocalTime startTime, LocalTime endTime) throws IOException;

    boolean removeAttendance(String studentId, String subject, LocalDate date, LocalTime startTime, LocalTime endTime) throws IOException;

    List<AttendanceRecord> findAttendanceByDate(LocalDate date, String subject) throws IOException;

    int countClassSessions(String subject) throws IOException;

    int countStudentAttendance(String studentId, String subject) throws IOException;

    List<ClassSession> findClassSessions(String subject) throws IOException;

    List<AttendanceRecord> findStudentAttendanceRecords(String studentId, String subject) throws IOException;

    void deleteClassSession(String subject, LocalDate date, LocalTime startTime) throws IOException;

    void updateActiveToken(String tokenValue) throws IOException;
    String getActiveToken() throws IOException;
}
