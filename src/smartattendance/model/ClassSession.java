package smartattendance.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class ClassSession {
    private final String subject;
    private final LocalDate date;
    private final String className;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String topic;

    public ClassSession(String subject, LocalDate date, String className, LocalTime startTime, LocalTime endTime,
                        String topic) {
        this.subject = subject;
        this.date = date;
        this.className = className;
        this.startTime = startTime;
        this.endTime = endTime;
        this.topic = topic;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getClassName() {
        return className;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getTopic() {
        return topic;
    }
}
