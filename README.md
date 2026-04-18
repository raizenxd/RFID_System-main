# RFID Attendance System (ICT-D_4)

This repository contains a Java Swing-based desktop application for RFID-based attendance tracking, built with Maven and using a MySQL database (XAMPP). 

## Project Structure

- `pom.xml` - Maven configuration
- `src/main/java/com/mycompany/ict/d_4/` - Java source files
- `src/main/java/com/mycompany/ict/d_4/*.form` - UI forms created by NetBeans form editor
- `target/` - compiled classes and built artifacts

## Key Components

### Main entry
- `ICTD_4.java` - application launcher, starts UI flow.

### Database connection
- `ConnectXamppSQL.java` - JDBC connection manager for MySQL (XAMPP)
  - `DriverManager.getConnection("jdbc:mysql://localhost/<db>?useSSL=false", user, pass)`
  - Singleton/managed connection usage

### Core business logic
- `AttendanceService.java` - check in/out logic and attendance persistence.
  - Validate RFID token
  - Avoid duplicate time-in/time-out
  - Save attendance records in `attendance_logs` table with `rfid_number`, `log_type`, `timestamp`
  - Send parent email notification for both IN/OUT events

### Email & checkin/check-out workflow
- Check-in and check-out are unified through `AttendanceService.processAttendance(rfid, logType, owner)`.
- `frmTimeIn` calls `processAttendance(<rfid>, "IN", this)` when Enter pressed.
- `frmTimeOut` calls `processAttendance(<rfid>, "OUT", this)` when Enter pressed.
- In `processAttendance`:
  - Student record loaded from `students_information` by `rfid_number`.
  - If student exists, insert row into `attendance_logs`:
    - `rfid_number` = scanned tag
    - `log_type` = "IN" or "OUT"
    - `timestamp` = current server datetime
  - Build email subject/body and send to `parent_email` (if present) using Gmail SMTP (`smtp.gmail.com:587`).
  - User receives toast-style message dialogs with success/error info.

### UI forms
- `MainPage.java` / `.form` - dashboard and navigation
- `frmRegister.java` / `.form` - add student with RFID
- `frmTimeIn.java` / `.form` - time in flow
- `frmTimeOut.java` / `.form` - time out flow

## App Flow

1. Start at `MainPage`.
2. Register students in `frmRegister`.
3. Record check-ins in `frmTimeIn`.
4. Record check-outs in `frmTimeOut`.

## Business Flow

1. App starts in `ICTD_4.main` and shows `MainPage`.
2. Registration: `frmRegister` stores student profile + RFID.
3. Time In: `frmTimeIn` records entry time.
4. Time Out: `frmTimeOut` records exit time.

## Database Schema (example)

```sql
CREATE DATABASE rfid_db;
USE rfid_db;

CREATE TABLE students (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  rfid VARCHAR(100) NOT NULL UNIQUE,
  grade VARCHAR(50),
  status VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE attendance (
  id INT AUTO_INCREMENT PRIMARY KEY,
  student_id INT NOT NULL,
  time_in DATETIME,
  time_out DATETIME,
  status VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(student_id) REFERENCES students(id)
);

CREATE TABLE unauthorized_reads (
  id INT AUTO_INCREMENT PRIMARY KEY,
  rfid VARCHAR(100) NOT NULL,
  detected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  note VARCHAR(255)
);
```

## Setup

1. Install Java JDK 8+ and Maven
2. Start XAMPP, run MySQL
3. Create the database schema (above SQL)
4. Update DB settings in `ConnectXamppSQL.java`
  - URL, username, password

## Build

```bash
mvn clean package
```

## Run

- From IDE: run `ICTD_4.java`
- From command line:

```bash
java -jar target/<artifact-name>.jar
```

## Notes

- Avoid creating multiple `Connection` objects per action; reuse the singleton connection.
- Add exception handling for SQL errors and connection timeouts.
- Log or display user-friendly errors when `429` or database issues happen.

## Enhancements

- Add pagination or filtered search to the attendance table.
- Add import/export CSV for attendance history.
- Add role-based access control (admin/instructor).
- Add unit tests for `AttendanceService` and DB operations.
