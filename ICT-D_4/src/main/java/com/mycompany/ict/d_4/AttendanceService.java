package com.mycompany.ict.d_4;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;

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
