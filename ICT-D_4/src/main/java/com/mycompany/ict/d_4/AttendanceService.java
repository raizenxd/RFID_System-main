package com.mycompany.ict.d_4;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;       // An ordered collection — used to hold multiple log rows fetched from the database
import java.util.Map;
import java.util.Properties;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class AttendanceService {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AttendanceService.class.getName());

    // TODO: externalize by config or secure store
    private static final String EMAIL_FROM = "inovejasraymondjoseph@gmail.com";
    private static final String EMAIL_PASSWORD = "ikee esfe flgd wwtx";
    private static final String SCHOOL_NAME = "Clark College of Science and Technology";
    private static final String SCHOOL_PHONE = "+63-912-345-6789";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    private static final java.util.concurrent.ConcurrentHashMap<String, LocalDateTime> lastScanTime
            = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int COOLDOWN_SECONDS = 15; // CHANGE THE COOLDOWN HERE (in seconds)


    public static void processAttendanceOneTap(String rfid, JFrame owner) {
        try {
            // STEP 1: Guard clause — ignore completely empty scans.
            if (rfid.trim().isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // Check if this RFID has been scanned before (it will be in the map if it has).
            if (lastScanTime.containsKey(rfid)) {
                // java.time.Duration.between() calculates the time gap between two moments.
                // .getSeconds() converts that gap into a whole number of seconds.
                long secondsSinceLast = java.time.Duration.between(lastScanTime.get(rfid), now).getSeconds();

                // If the gap is less than the cooldown, the tap is too fast — block it.
                if (secondsSinceLast < COOLDOWN_SECONDS) {
                    long remaining = COOLDOWN_SECONDS - secondsSinceLast; // Seconds left to wait
                    showTimedMessageDialog(
                        owner,
                        "Too fast! Please wait " + remaining + " more second(s) before scanning again.",
                        "Cooldown Active",
                        JOptionPane.WARNING_MESSAGE
                    );
                    return; // Stop here — do NOT process this scan
                }
            }


            lastScanTime.put(rfid, now);

            Map<String, String> student = ConnectXamppSQL.Read("students_information")
                    .where("rfid_number", "=", rfid)
                    .getOne(); // Returns the matching row, or null if the card is not registered

            // If the card is not registered, show the unauthorized screen and stop.
            if (student == null) {
                new frmUnauthorizedStudent().setVisible(true);
                return;
            }

            // STEP 5: Extract the student's details from the database result.
            String firstname   = student.getOrDefault("firstname",    "");
            String lastname    = student.getOrDefault("lastname",     "");
            String parentEmail = student.getOrDefault("parent_email", "");
            String studentName = (firstname + " " + lastname).trim();

            // todayDate = just "yyyy-MM-dd" part of now — used to filter logs by today only.
            String todayDate  = now.format(DATE_FORMAT);
            String timestamp  = now.format(TIMESTAMP_FORMAT); // Full timestamp for DB storage

            List<Map<String, String>> allLogs = ConnectXamppSQL.Read("attendance_logs")
                    .where("rfid_number", "=", rfid)
                    .get();


            boolean hasInToday = allLogs.stream()
                    .anyMatch(log -> "IN".equalsIgnoreCase(log.getOrDefault("log_type", ""))
                            && log.getOrDefault("timestamp", "").startsWith(todayDate));

            boolean hasOutToday = allLogs.stream()
                    .anyMatch(log -> "OUT".equalsIgnoreCase(log.getOrDefault("log_type", ""))
                            && log.getOrDefault("timestamp", "").startsWith(todayDate));

            // Decide the log type based on what already exists for today:
            String logType;
            if (!hasInToday) {
                logType = "IN";  // No IN yet today → this is the student's arrival tap
            } else if (!hasOutToday) {
                logType = "OUT"; // Already IN but no OUT yet → this is the student's departure tap
            } else {
                // Both IN and OUT already recorded — attendance is fully done for today.
                showTimedMessageDialog(
                    owner,
                    studentName + " has already completed attendance for today.",
                    "Attendance Complete",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return; // Do NOT insert another record
            }

            ConnectXamppSQL.Insert("attendance_logs")
                    .set("rfid_number", rfid)
                    .set("log_type",    logType)   // Either "IN" or "OUT" as decided above
                    .set("timestamp",   timestamp)
                    .execute();

            String subject = "School Attendance Notification: " + studentName + " - Time " + logType;
            String body = "Dear Parent/Guardian,\n\n"
                    + "This is an automated notification to inform you that " + studentName
                    + " has safely " + ("IN".equals(logType) ? "arrived at school and timed in" : "left school and timed out")
                    + " at " + now.format(TIME_FORMAT)
                    + " today, " + now.format(DATE_FORMAT) + ".\n\n"
                    + "If you have any questions regarding this notification, please contact the school office at " + SCHOOL_PHONE + ".\n\n"
                    + "Best regards,\n"
                    + SCHOOL_NAME + "\n"
                    + "Attendance Office";

            if (!parentEmail.isBlank()) {
                sendGmailNotification(parentEmail, subject, body);
            } else {
                logger.warning("Parent email is empty for RFID " + rfid);
            }

            showTimedMessageDialog(
                owner,
                "Time-" + logType.toLowerCase() + " recorded for " + studentName,
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception ex) {
            logger.log(java.util.logging.Level.SEVERE, "Error processing one-tap attendance", ex);
        }
    }

    public static void processAttendance(String rfid, String logType, JFrame owner) {
        try {
            if (rfid.trim().isEmpty()) {        
                return;
            }

            Map<String, String> student = ConnectXamppSQL.Read("students_information")
                    .where("rfid_number", "=", rfid)
                    .getOne();

            if (student == null) {
                new frmUnauthorizedStudent().setVisible(true);
            }


            String firstname = student.getOrDefault("firstname", "");
            String lastname = student.getOrDefault("lastname", "");
            String parentEmail = student.getOrDefault("parent_email", "");
            String studentName = (firstname + " " + lastname).trim();
            LocalDateTime now = LocalDateTime.now();
            String timestamp = now.format(TIMESTAMP_FORMAT);

            ConnectXamppSQL.Insert("attendance_logs")
                    .set("rfid_number", rfid)
                    .set("log_type", logType)
                    .set("timestamp", timestamp)
                    .execute();

            String subject = "School Attendance Notification: " + studentName + " - Time " + logType;
            String body = "Dear Parent/Guardian,\n\n"
                    + "This is an automated notification to inform you that " + studentName
                    + " has safely " + ("IN".equals(logType) ? "arrived at school and timed in" : "left school and timed out")
                    + " at " + now.format(TIME_FORMAT)
                    + " today, " + now.format(DATE_FORMAT) + ".\n\n"
                    + "If you have any questions regarding this notification, please contact the school office at " + SCHOOL_PHONE + ".\n\n"
                    + "Best regards,\n"
                    + SCHOOL_NAME + "\n"
                    + "Attendance Office";

            if (!parentEmail.isBlank()) {
                sendGmailNotification(parentEmail, subject, body);
            } 
            
            else {
                logger.warning("Parent email is empty for RFID " + rfid);
            }

                showTimedMessageDialog(owner, "Time-" + logType.toLowerCase() + " recorded and notification sent for " + studentName, "Success", JOptionPane.INFORMATION_MESSAGE);
        } 
        
        catch (Exception ex) {
            logger.log(java.util.logging.Level.SEVERE, "Error processing RFID time-" + logType.toLowerCase(), ex);
        }
    }

    private static void showTimedMessageDialog(JFrame owner, String message, String title, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType);
        JDialog dialog = pane.createDialog(owner, title);
        dialog.setModal(false);
        // CHANGE THE TIMER HERE
        Timer timer = new Timer(3000, e -> dialog.dispose());
        timer.setRepeats(false);
        timer.start();

        dialog.setVisible(true);
    }

    public static void sendGmailNotification(String toEmail, String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            logger.info("Email notification sent to " + toEmail + ": " + subject);
        } catch (MessagingException ex) {
            logger.log(java.util.logging.Level.SEVERE, "Failed to send email notification", ex);
        }
    }
}
