package smartattendance.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AttendanceRecord {
    private final String studentId;
    private final String studentName;
    private final String subject;
    private final LocalDate attendanceDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final LocalDateTime markedAt;

    public AttendanceRecord(String studentId, String studentName, String subject, LocalDate attendanceDate,
                            LocalTime startTime, LocalTime endTime, LocalDateTime markedAt) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.subject = subject;
        this.attendanceDate = attendanceDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.markedAt = markedAt;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }
}
