# Smart Attendance Management System

A complete Java project for the Java syllabus. The system has three users: admin, teacher, and students. The admin registers teachers and students. The teacher logs in, selects a subject, and displays a QR code that refreshes every 5 seconds. Students log in first, view their subject-wise attendance percentage, and then scan the current QR code to mark attendance.

## Features

- Admin login ID and password system.
- Teacher login ID and password system.
- Student login ID and password system.
- Java Swing desktop dashboards for admin and teacher.
- QR code refresh every 5 seconds using `ScheduledExecutorService`.
- Built-in student login/dashboard website using JDK `HttpServer`.
- Student login ID and password management.
- Subject-wise attendance percentage for students.
- Attendance saved with subject, date, and time.
- Duplicate attendance blocked for the same student in the same subject on the same day.
- File storage by default using CSV files.
- JDBC-ready store class for database syllabus explanation.
- Uses OOP concepts: classes, interfaces, encapsulation, abstraction, inheritance-ready design, method calls, exception handling, collections, threads, and I/O.

## Folder Structure

```text
src/smartattendance/
  Main.java
  model/
    Student.java
    Teacher.java
    Admin.java
    AttendanceRecord.java
  store/
    AttendanceStore.java
    FileAttendanceStore.java
    JdbcAttendanceStore.java
  ui/
    AdminFrame.java
    UserManagementFrame.java
  web/
    AttendanceServer.java
    TokenService.java
  qr/
    QrCodeGenerator.java
data/
  admins.csv
  teachers.csv
  students.csv
  subjects.csv
  class_sessions.csv
  attendance.csv
scripts/
  compile.sh
  run.sh
```

## Requirements

- JDK 17 or newer.
- Phone and laptop should be on the same Wi-Fi network for phone scanning.

## How To Run

From the project folder:

```bash
chmod +x scripts/compile.sh scripts/run.sh
./scripts/run.sh
```

## Enable Live Mobile Camera Scanner

Mobile browsers require HTTPS for live camera access. Run this once before class:

```bash
chmod +x scripts/setup-https.sh
./scripts/setup-https.sh
```

Then restart the app:

```bash
./scripts/run.sh
```

Students should open the `https://.../student-login` URL shown in the teacher dashboard. On the first visit, the phone may show a certificate warning because this is a local classroom certificate. Continue to the site, then the browser can ask for camera permission on the QR scanner page.

Or manually:

```bash
mkdir -p out
javac -d out $(find src -name "*.java")
java -cp out smartattendance.Main
```

## Demo Login IDs

Admin login:

| Admin ID | Password |
| --- | --- |
| ADM001 | admin123 |

Teacher login:

| Teacher ID | Password |
| --- | --- |
| TCH001 | teacher123 |

Student login:

| Student ID | Name | Password |
| --- | --- | --- |
| CSE001 | Aarav Kumar | pass001 |
| CSE002 | Diya Sharma | pass002 |
| CSE003 | Rohan Das | pass003 |

Admin can add new teachers and new students from the admin dashboard.

## How It Works

1. The app first asks whether the user is Admin or Teacher.
2. Admin logs in and can register new teachers and students.
3. Teacher logs in using teacher ID and password.
4. The app starts a local web server on port `8080`.
5. The teacher selects a subject and starts the QR.
6. A secure random token is generated and encoded into a QR code.
7. Every 5 seconds, the token changes and the QR code refreshes.
8. A student opens the student login URL and logs in.
9. The student dashboard shows subject-wise attendance percentage and date-wise present/absent status.
10. The student opens the live QR scanner and scans the current QR code.
11. The server checks the login session, token, and subject.
12. Attendance is saved in `data/attendance.csv`.

## Syllabus Mapping

- Module I: data types, variables, arrays/lists, operators, control flow, OOP, classes, objects, constructors, static methods, final fields, string handling.
- Module II: interfaces through `AttendanceStore`, packages, access modifiers, Java I/O through CSV storage, streams/readers/writers.
- Module III: exception handling with `try/catch`, checked exceptions, multithreading with scheduled token refresh and HTTP request threads.
- Module IV: wrapper classes, collection framework (`List`, `Map`, `Optional`), JDBC concepts in `JdbcAttendanceStore`, Swing/AWT GUI classes and event handling.

## Notes For Presentation

This project is intentionally built with only the standard JDK so it can run without Maven or external libraries. The default storage is file-based. The `JdbcAttendanceStore` file demonstrates JDBC concepts and can be connected to a database by adding a suitable JDBC driver.
