# Smart Attendance Management System Project Report

## Objective

The objective of this project is to develop a Java-based smart attendance system with separate teacher and student users. The teacher logs in, selects a subject, and displays a time-limited QR code. Students log in first, view their subject-wise attendance percentage, and then scan the QR code to mark attendance.

## Problem Statement

Traditional attendance marking is slow and can be manipulated by proxy attendance. This system reduces manual effort by using a QR code that changes every 5 seconds. Only logged-in students who scan the current QR can mark attendance. The system also calculates attendance percentage for each subject.

## Modules

## 1. Admin Module

The teacher module starts with a teacher login screen. After login, the teacher dashboard displays the QR code, current token, countdown timer, subject selector, student list, and today's attendance records for the selected subject.

## 2. QR Token Module

The token module uses `SecureRandom` to generate a random token. `ScheduledExecutorService` changes the token every 5 seconds. The current token is converted into a QR image.

## 3. Student Web Module

The student module runs through Java's built-in `HttpServer`. The student logs in first using student ID and password. After login, the dashboard shows subject-wise present classes, total classes, and attendance percentage. The student can then scan the teacher's QR code to mark attendance.

## 4. Storage Module

The project uses an `AttendanceStore` interface. `FileAttendanceStore` saves teachers, students, subjects, class sessions, and attendance in CSV files. `JdbcAttendanceStore` demonstrates how the same system can be connected to a database using JDBC.

## Important Classes

| Class | Purpose |
| --- | --- |
| `Main` | Starts storage, token service, web server, and GUI |
| `Student` | Stores student ID, name, and password |
| `Teacher` | Stores teacher ID, name, and password |
| `AttendanceRecord` | Stores one attendance entry |
| `AttendanceStore` | Interface for storage abstraction |
| `FileAttendanceStore` | File I/O implementation |
| `JdbcAttendanceStore` | JDBC implementation example |
| `TokenService` | Generates and refreshes tokens |
| `AttendanceServer` | Handles student login, dashboard, scan, and attendance marking |
| `QrCodeGenerator` | Creates QR image using Java |
| `TeacherLoginDialog` | Teacher login screen |
| `AdminFrame` | Swing teacher dashboard |

## OOP Concepts Used

- Encapsulation: fields are private and accessed through methods.
- Abstraction: `AttendanceStore` hides storage details.
- Polymorphism: different store classes can implement the same interface.
- Classes and objects: each real-world entity has a class.
- Constructors: used to initialize objects.
- Static methods: used for utility behavior.
- Final fields: used for values that should not change.

## Exception Handling

The project handles file, database, and web server exceptions using checked exceptions such as `IOException` and `SQLException`. Invalid student input is handled with `IllegalArgumentException`.

## Multithreading

The QR token refresh runs on a scheduled background thread. The HTTP server also handles student requests using a thread pool.

## I/O and Database Concepts

The default project stores data in CSV files using `BufferedReader` and `BufferedWriter`. The JDBC class demonstrates `Connection`, `Statement`, `PreparedStatement`, and `ResultSet`. The attendance percentage is calculated as:

```text
Attendance Percentage = Present Classes / Total Classes x 100
```

## Conclusion

The Smart Attendance Management System is a complete Java project that combines teacher login, student login, subject-wise attendance, percentage calculation, GUI programming, OOP, file handling, exception handling, multithreading, collections, QR generation, and database concepts. It is suitable for demonstrating the main topics from the Java syllabus.
