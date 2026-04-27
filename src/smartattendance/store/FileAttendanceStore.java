package smartattendance.store;

import smartattendance.model.AttendanceRecord;
import smartattendance.model.Admin;
import smartattendance.model.ClassSession;
import smartattendance.model.Student;
import smartattendance.model.Teacher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileAttendanceStore implements AttendanceStore {
    private final Path dataDirectory;
    private final Path adminsFile;
    private final Path studentsFile;
    private final Path teachersFile;
    private final Path subjectsFile;
    private final Path sessionsFile;
    private final Path attendanceFile;

    public FileAttendanceStore(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.adminsFile = dataDirectory.resolve("admins.csv");
        this.studentsFile = dataDirectory.resolve("students.csv");
        this.teachersFile = dataDirectory.resolve("teachers.csv");
        this.subjectsFile = dataDirectory.resolve("subjects.csv");
        this.sessionsFile = dataDirectory.resolve("class_sessions.csv");
        this.attendanceFile = dataDirectory.resolve("attendance.csv");
    }

    @Override
    public synchronized void initialize() throws IOException {
        Files.createDirectories(dataDirectory);
        if (Files.notExists(adminsFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(adminsFile, StandardCharsets.UTF_8)) {
                writer.write("id,name,password");
                writer.newLine();
                writer.write("ADM001,Soumya,soumyar@njan");
                writer.newLine();
            }
        }
        if (Files.notExists(studentsFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(studentsFile, StandardCharsets.UTF_8)) {
                writer.write("id,name,password");
                writer.newLine();
                writer.write("CSE001,Aarav Kumar,pass001");
                writer.newLine();
                writer.write("CSE002,Diya Sharma,pass002");
                writer.newLine();
                writer.write("CSE003,Rohan Das,pass003");
                writer.newLine();
            }
        }
        if (Files.notExists(teachersFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(teachersFile, StandardCharsets.UTF_8)) {
                writer.write("id,name,password");
                writer.newLine();
                writer.write("TCH001,Java Teacher,teacher123");
                writer.newLine();
            }
        }
        if (Files.notExists(subjectsFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(subjectsFile, StandardCharsets.UTF_8)) {
                writer.write("subject");
                writer.newLine();
            }
        }
        if (Files.notExists(sessionsFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(sessionsFile, StandardCharsets.UTF_8)) {
                writer.write("subject,date,className,startTime,endTime,durationMinutes,topic");
                writer.newLine();
            }
        }
        if (Files.notExists(attendanceFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(attendanceFile, StandardCharsets.UTF_8)) {
                writer.write("studentId,studentName,subject,date,startTime,endTime,markedAt");
                writer.newLine();
            }
        }
    }

    @Override
    public synchronized List<Student> findAllStudents() throws IOException {
        List<Student> students = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(studentsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 3) {
                    students.add(new Student(columns.get(0), columns.get(1), columns.get(2)));
                }
            }
        }
        return students;
    }

    @Override
    public synchronized Optional<Student> findStudentById(String studentId) throws IOException {
        return findAllStudents().stream()
                .filter(student -> student.getId().equalsIgnoreCase(studentId.trim()))
                .findFirst();
    }

    @Override
    public synchronized List<Teacher> findAllTeachers() throws IOException {
        List<Teacher> teachers = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(teachersFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 3) {
                    teachers.add(new Teacher(columns.get(0), columns.get(1), columns.get(2)));
                }
            }
        }
        return teachers;
    }

    @Override
    public synchronized Optional<Teacher> findTeacherById(String teacherId) throws IOException {
        return findAllTeachers().stream()
                .filter(teacher -> teacher.getId().equalsIgnoreCase(teacherId.trim()))
                .findFirst();
    }

    @Override
    public synchronized Optional<Admin> findAdminById(String adminId) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(adminsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 3 && columns.get(0).equalsIgnoreCase(adminId.trim())) {
                    return Optional.of(new Admin(columns.get(0), columns.get(1), columns.get(2)));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<String> findAllSubjects() throws IOException {
        List<String> subjects = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(subjectsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (!columns.isEmpty() && !columns.get(0).isBlank()) {
                    subjects.add(columns.get(0));
                }
            }
        }
        return subjects;
    }

    @Override
    public synchronized void saveSubject(String subject) throws IOException {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be empty");
        }
        String cleanSubject = subject.trim();
        List<String> subjects = findAllSubjects();
        for (String existing : subjects) {
            if (existing.equalsIgnoreCase(cleanSubject)) {
                return;
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(subjectsFile, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(toCsv(cleanSubject));
            writer.newLine();
        }
    }

    @Override
    public synchronized List<String> findSubjectsForStudent(String studentId) throws IOException {
        List<String> subjects = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(attendanceFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 5 && columns.get(0).equalsIgnoreCase(studentId)) {
                    boolean exists = false;
                    for (String subject : subjects) {
                        if (subject.equalsIgnoreCase(columns.get(2))) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        subjects.add(columns.get(2));
                    }
                }
            }
        }
        return subjects;
    }

    @Override
    public synchronized void saveStudent(Student student) throws IOException {
        Map<String, Student> studentsById = new LinkedHashMap<>();
        for (Student existing : findAllStudents()) {
            studentsById.put(existing.getId().toUpperCase(), existing);
        }
        studentsById.put(student.getId().toUpperCase(), student);

        try (BufferedWriter writer = Files.newBufferedWriter(studentsFile, StandardCharsets.UTF_8)) {
            writer.write("id,name,password");
            writer.newLine();
            for (Student value : studentsById.values()) {
                writer.write(toCsv(value.getId(), value.getName(), value.getPassword()));
                writer.newLine();
            }
        }
    }

    @Override
    public synchronized void saveTeacher(Teacher teacher) throws IOException {
        Map<String, Teacher> teachersById = new LinkedHashMap<>();
        for (Teacher existing : findAllTeachers()) {
            teachersById.put(existing.getId().toUpperCase(), existing);
        }
        teachersById.put(teacher.getId().toUpperCase(), teacher);

        try (BufferedWriter writer = Files.newBufferedWriter(teachersFile, StandardCharsets.UTF_8)) {
            writer.write("id,name,password");
            writer.newLine();
            for (Teacher value : teachersById.values()) {
                writer.write(toCsv(value.getId(), value.getName(), value.getPassword()));
                writer.newLine();
            }
        }
    }

    @Override
    public synchronized void deleteStudent(String studentId) throws IOException {
        List<Student> students = findAllStudents();
        students.removeIf(s -> s.getId().equalsIgnoreCase(studentId.trim()));
        
        try (BufferedWriter writer = Files.newBufferedWriter(studentsFile, StandardCharsets.UTF_8)) {
            writer.write("id,name,password");
            writer.newLine();
            for (Student s : students) {
                writer.write(toCsv(s.getId(), s.getName(), s.getPassword()));
                writer.newLine();
            }
        }
    }

    @Override
    public synchronized void deleteTeacher(String teacherId) throws IOException {
        List<Teacher> teachers = findAllTeachers();
        teachers.removeIf(t -> t.getId().equalsIgnoreCase(teacherId.trim()));
        
        try (BufferedWriter writer = Files.newBufferedWriter(teachersFile, StandardCharsets.UTF_8)) {
            writer.write("id,name,password");
            writer.newLine();
            for (Teacher t : teachers) {
                writer.write(toCsv(t.getId(), t.getName(), t.getPassword()));
                writer.newLine();
            }
        }
    }

    @Override
    public synchronized void recordClassSession(String subject, LocalDate date, String className,
                                               LocalTime startTime, LocalTime endTime, String topic) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(sessionsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                LocalTime existingStart = readStartTime(columns);
                LocalTime existingEnd = readEndTime(columns);
                if (columns.size() >= 2
                        && columns.get(0).equalsIgnoreCase(subject)
                        && LocalDate.parse(columns.get(1)).equals(date)
                        && existingStart.equals(startTime)
                        && existingEnd.equals(endTime)) {
                    return;
                }
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(sessionsFile, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND)) {
            int durationMinutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
            writer.write(toCsv(subject, date.toString(), className, startTime.toString(), endTime.toString(),
                    String.valueOf(durationMinutes), cleanTopic(topic)));
            writer.newLine();
        }
    }

    @Override
    public synchronized boolean markAttendance(Student student, String subject, LocalDate date,
                                               LocalTime startTime, LocalTime endTime) throws IOException {
        for (AttendanceRecord record : findAttendanceByDate(date, subject)) {
            if (record.getStudentId().equalsIgnoreCase(student.getId())
                    && record.getSubject().equalsIgnoreCase(subject)
                    && record.getStartTime().equals(startTime)
                    && record.getEndTime().equals(endTime)) {
                return false;
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(attendanceFile, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(toCsv(student.getId(), student.getName(), subject, date.toString(),
                    startTime.toString(), endTime.toString(), LocalDateTime.now().toString()));
            writer.newLine();
        }
        return true;
    }

    @Override
    public synchronized boolean removeAttendance(String studentId, String subject, LocalDate date,
                                                 LocalTime startTime, LocalTime endTime) throws IOException {
        List<String> keptLines = new ArrayList<>();
        boolean removed = false;
        try (BufferedReader reader = Files.newBufferedReader(attendanceFile, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            keptLines.add("studentId,studentName,subject,date,startTime,endTime,markedAt");
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                boolean matches = columns.size() >= 5
                        && columns.get(0).equalsIgnoreCase(studentId)
                        && columns.get(2).equalsIgnoreCase(subject)
                        && LocalDate.parse(columns.get(3)).equals(date)
                        && readAttendanceStartTime(columns).equals(startTime)
                        && readAttendanceEndTime(columns).equals(endTime);
                if (matches) {
                    removed = true;
                } else {
                    keptLines.add(line);
                }
            }
        }

        if (removed) {
            try (BufferedWriter writer = Files.newBufferedWriter(attendanceFile, StandardCharsets.UTF_8)) {
                for (String line : keptLines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
        return removed;
    }

    @Override
    public synchronized List<AttendanceRecord> findAttendanceByDate(LocalDate date, String subject) throws IOException {
        List<AttendanceRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(attendanceFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 5
                        && columns.get(2).equalsIgnoreCase(subject)
                        && LocalDate.parse(columns.get(3)).equals(date)) {
                    records.add(new AttendanceRecord(columns.get(0), columns.get(1),
                            columns.get(2), LocalDate.parse(columns.get(3)), readAttendanceStartTime(columns),
                            readAttendanceEndTime(columns), readMarkedAt(columns)));
                }
            }
        }
        return records;
    }

    @Override
    public synchronized int countClassSessions(String subject) throws IOException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(sessionsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 2 && columns.get(0).equalsIgnoreCase(subject)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public synchronized int countStudentAttendance(String studentId, String subject) throws IOException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(attendanceFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 5
                        && columns.get(0).equalsIgnoreCase(studentId)
                        && columns.get(2).equalsIgnoreCase(subject)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public synchronized List<ClassSession> findClassSessions(String subject) throws IOException {
        List<ClassSession> sessions = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(sessionsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 2 && columns.get(0).equalsIgnoreCase(subject)) {
                    sessions.add(new ClassSession(columns.get(0), LocalDate.parse(columns.get(1)),
                            readClassName(columns), readStartTime(columns), readEndTime(columns), readTopic(columns)));
                }
            }
        }
        sessions.sort((left, right) -> {
            int dateCompare = left.getDate().compareTo(right.getDate());
            if (dateCompare != 0) {
                return dateCompare;
            }
            return left.getStartTime().compareTo(right.getStartTime());
        });
        return sessions;
    }

    @Override
    public synchronized List<AttendanceRecord> findStudentAttendanceRecords(String studentId, String subject) throws IOException {
        List<AttendanceRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(attendanceFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsv(line);
                if (columns.size() >= 5
                        && columns.get(0).equalsIgnoreCase(studentId)
                        && columns.get(2).equalsIgnoreCase(subject)) {
                    records.add(new AttendanceRecord(columns.get(0), columns.get(1),
                            columns.get(2), LocalDate.parse(columns.get(3)), readAttendanceStartTime(columns),
                            readAttendanceEndTime(columns), readMarkedAt(columns)));
                }
            }
        }
        records.sort((left, right) -> {
            int dateCompare = left.getAttendanceDate().compareTo(right.getAttendanceDate());
            if (dateCompare != 0) {
                return dateCompare;
            }
            return left.getStartTime().compareTo(right.getStartTime());
        });
        return records;
    }

    private static String readClassName(List<String> columns) {
        return columns.size() >= 3 ? columns.get(2) : "";
    }

    private static LocalTime readStartTime(List<String> columns) {
        return columns.size() >= 5 ? LocalTime.parse(columns.get(3)) : LocalTime.of(9, 0);
    }

    private static LocalTime readEndTime(List<String> columns) {
        return columns.size() >= 5 ? LocalTime.parse(columns.get(4)) : LocalTime.of(10, 0);
    }

    private static LocalTime readAttendanceStartTime(List<String> columns) {
        return columns.size() >= 7 ? LocalTime.parse(columns.get(4)) : LocalTime.of(9, 0);
    }

    private static LocalTime readAttendanceEndTime(List<String> columns) {
        return columns.size() >= 7 ? LocalTime.parse(columns.get(5)) : LocalTime.of(10, 0);
    }

    private static LocalDateTime readMarkedAt(List<String> columns) {
        return columns.size() >= 7 ? LocalDateTime.parse(columns.get(6)) : LocalDateTime.parse(columns.get(4));
    }

    private static String readTopic(List<String> columns) {
        return columns.size() >= 7 && !columns.get(6).isBlank() ? columns.get(6) : "Not specified";
    }

    private static String cleanTopic(String topic) {
        return topic == null || topic.isBlank() ? "Not specified" : topic.trim();
    }

    private static String toCsv(String... values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            String safe = value == null ? "" : value;
            if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
                safe = "\"" + safe.replace("\"", "\"\"") + "\"";
            }
            escaped.add(safe);
        }
        return String.join(",", escaped);
    }

    private static List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }
}
