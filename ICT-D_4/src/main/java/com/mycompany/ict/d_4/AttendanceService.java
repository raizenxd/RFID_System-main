package com.mycompany.ict.d_4;

// ── JAKARTA MAIL IMPORTS ──────────────────────────────────────────────────────
// Jakarta Mail is a Java library that lets your program send emails automatically.
// Think of it as giving Java the ability to act like an email client (like Gmail).
import java.time.LocalDateTime;           // Handles login — provides username + password to the mail server
import java.time.format.DateTimeFormatter;                 // Represents one email message (like writing a letter)
import java.util.Map;      // The error/exception type thrown if email sending fails
import java.util.Properties;  // Packages the email address + password for the Authenticator

import javax.swing.JDialog;                 // The "connection" to the mail server — holds all SMTP settings
import javax.swing.JFrame;               // The actual "sender" — delivers the finished email to the server
import javax.swing.JOptionPane; // Parses and validates standard email addresses (user@domain.com)
import javax.swing.Timer;    // A standard MIME-format email (supports plain text, HTML, attachments)

import jakarta.mail.Authenticator;              // Captures the current date AND time (e.g., 2026-04-18T08:30:00)
import jakarta.mail.Message;   // Converts LocalDateTime into a readable string (e.g., "08:30 AM")
import jakarta.mail.MessagingException;                        // A key→value data structure; used here to hold one database row
import jakarta.mail.PasswordAuthentication;                // Stores text-based settings as key=value pairs (used for SMTP config)
import jakarta.mail.Session;                 // A secondary popup window — lighter than a full JFrame
import jakarta.mail.Transport;                  // The main application window type in Java Swing GUI apps
import jakarta.mail.internet.InternetAddress;             // Provides ready-made dialog boxes (info, warning, error popups)
import jakarta.mail.internet.MimeMessage;                   // Fires a callback once after a delay — used to auto-close dialogs

// ── AttendanceService CLASS ───────────────────────────────────────────────────
// PURPOSE: This class is the "brain" of the attendance system.
// It has two main jobs:
//   1. Record student time-in/time-out events into the database
//   2. Send an email notification to the student's parent after each scan
//
// It is a "utility" class — all methods are static, meaning you call them
// directly as AttendanceService.processAttendance(...) without creating an object.
public class AttendanceService {

    // Logger: a professional way to print messages to the console.
    // Instead of System.out.println, a Logger lets us tag messages as INFO,
    // WARNING, or SEVERE — making it much easier to find and fix bugs.
    // AttendanceService.class.getName() sets the logger's name to match this class.
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AttendanceService.class.getName());

    // ── EMAIL CONFIGURATION ───────────────────────────────────────────────────
    // These are the sender's Gmail credentials used to send parent notifications.
    // "static" means they belong to the class itself, not any object created from it.
    // "final" means their values are locked — they cannot be changed after being set.
    // ⚠ WARNING: Hardcoding passwords directly in source code is a security risk!
    //   In a real production system, store credentials in environment variables or a
    //   secure config file — NEVER directly in the code where others can read them.
    private static final String EMAIL_FROM     = "inovejasraymondjoseph@gmail.com"; // Gmail address that sends the notification
    private static final String EMAIL_PASSWORD = "ikee esfe flgd wwtx";             // Gmail App Password (NOT your regular Gmail password!)
    private static final String SCHOOL_NAME    = "Clark College of Science and Technology"; // Shown at the bottom of every email
    private static final String SCHOOL_PHONE   = "+63-912-345-6789";                // Contact number included in the email body

    // ── DATE/TIME FORMATTERS ──────────────────────────────────────────────────
    // DateTimeFormatter defines a PATTERN that controls how a date/time looks as text.
    // "hh" = 12-hour clock hour, "mm" = minutes, "a" = AM or PM → produces "08:30 AM"
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    // "yyyy" = 4-digit year, "MM" = 2-digit month, "dd" = 2-digit day → "2026-04-18"
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    // Full database timestamp format used when saving to MySQL → "2026-04-18 08:30:00"
    // "HH" = 24-hour clock (important for database timestamps)
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── processAttendance() ───────────────────────────────────────────────────
    // WHAT IT DOES: Main entry point called every time a student scans their RFID card.
    //
    // PARAMETERS:
    //   rfid    → The RFID card number read by the scanner (e.g., "1234567890")
    //   logType → "IN" when the student is arriving, "OUT" when they are leaving
    //   owner   → The window that called this method (needed to position the popup dialog)
    //
    // FLOW (step by step):
    //   Step 1 → Validate the RFID (don't process if it's blank/empty)
    //   Step 2 → Look up the student in the database using the RFID number
    //   Step 3 → If no student found, show the Unauthorized Student screen
    //   Step 4 → Extract the student's name and parent email from the result
    //   Step 5 → Get the current date/time and format it for database storage
    //   Step 6 → Insert a new attendance log record into the database
    //   Step 7 → Build the email subject and body text
    //   Step 8 → Send the email notification to the parent
    //   Step 9 → Show a timed confirmation popup on screen
    public static void processAttendance(String rfid, String logType, JFrame owner) {
        try {
            // STEP 1: Guard clause — if RFID is blank or only whitespace, stop immediately.
            // .trim() removes leading/trailing spaces; .isEmpty() checks if nothing remains.
            if (rfid.trim().isEmpty()) {        
                return; // Exit the method early — there is nothing to process
            }

            // STEP 2: Search the database for a student whose rfid_number matches the scanned card.
            // ConnectXamppSQL.Read() builds a SELECT query targeting the given table.
            // .where() adds a WHERE filter condition to narrow down results.
            // .getOne() executes the query and returns the first matching row as a Map<String,String>.
            // A Map is like a dictionary: key = column name, value = that column's cell value.
            Map<String, String> student = ConnectXamppSQL.Read("students_information")
                    .where("rfid_number", "=", rfid) // SQL: WHERE rfid_number = 'rfid'
                    .getOne();                        // Returns one Map row, or null if no match found

            // STEP 3: If no student was found with this RFID, show the unauthorized student window.
            // This alerts the admin that an unknown or unregistered card was scanned.
            if (student == null) {
                new frmUnauthorizedStudent().setVisible(true); // Open the unauthorized screen
            }

            // STEP 4: Pull the student's details from the database result (the Map).
            // .getOrDefault(key, defaultValue) safely returns the value for that column,
            // or the default ("") if the column is missing or null — avoids NullPointerException.
            String firstname   = student.getOrDefault("firstname", "");     // e.g., "Juan"
            String lastname    = student.getOrDefault("lastname", "");      // e.g., "Dela Cruz"
            String parentEmail = student.getOrDefault("parent_email", ""); // e.g., "parent@gmail.com"
            // Combine first + last name into a full name, trimming any extra spaces
            String studentName = (firstname + " " + lastname).trim();       // e.g., "Juan Dela Cruz"

            // STEP 5: Capture the exact moment the scan happened.
            // LocalDateTime.now() reads the current date and time from the computer's clock.
            LocalDateTime now = LocalDateTime.now();
            // Format it as a full timestamp string suitable for saving into MySQL
            String timestamp = now.format(TIMESTAMP_FORMAT); // e.g., "2026-04-18 08:30:00"

            // STEP 6: Insert a new row into the attendance_logs table in the database.
            // This permanently records WHO scanned (rfid), WHAT happened (logType),
            // and WHEN it happened (timestamp).
            ConnectXamppSQL.Insert("attendance_logs")
                    .set("rfid_number", rfid)       // Column rfid_number = the scanned card number
                    .set("log_type", logType)        // Column log_type = "IN" or "OUT"
                    .set("timestamp", timestamp)     // Column timestamp = "2026-04-18 08:30:00"
                    .execute();                      // Run the actual SQL INSERT statement

            // STEP 7A: Build the email subject line.
            // String concatenation (+) joins all pieces into one complete string.
            // Example result: "School Attendance Notification: Juan Dela Cruz - Time IN"
            String subject = "School Attendance Notification: " + studentName + " - Time " + logType;

            // STEP 7B: Build the full email body — the complete message parents will read.
            // The ternary operator (condition ? valueIfTrue : valueIfFalse) picks the right phrase
            // depending on whether the student is arriving (IN) or leaving (OUT).
            // "\n\n" inserts blank lines between paragraphs in the email body.
            String body = "Dear Parent/Guardian,\n\n"
                    + "This is an automated notification to inform you that " + studentName
                    + " has safely " + ("IN".equals(logType) ? "arrived at school and timed in" : "left school and timed out")
                    + " at " + now.format(TIME_FORMAT)             // e.g., "08:30 AM"
                    + " today, " + now.format(DATE_FORMAT) + ".\n\n" // e.g., "2026-04-18"
                    + "If you have any questions regarding this notification, please contact the school office at " + SCHOOL_PHONE + ".\n\n"
                    + "Best regards,\n"
                    + SCHOOL_NAME + "\n"
                    + "Attendance Office";

            // STEP 8: Only attempt to send an email if the parent's email is not blank.
            // .isBlank() returns true for empty strings AND strings that contain only spaces.
            if (!parentEmail.isBlank()) {
                sendGmailNotification(parentEmail, subject, body); // Send the email via Gmail SMTP
            } 
            
            else {
                // If no email address was saved for this student, log a warning.
                // This does NOT crash the program — it just records a note in the console.
                logger.warning("Parent email is empty for RFID " + rfid);
            }

            // STEP 9: Show a success popup that automatically closes after 3 seconds.
            // .toLowerCase() converts "IN" → "in" or "OUT" → "out" for nicer display.
            // Example message: "Time-in recorded and notification sent for Juan Dela Cruz"
            showTimedMessageDialog(owner, "Time-" + logType.toLowerCase() + " recorded and notification sent for " + studentName, "Success", JOptionPane.INFORMATION_MESSAGE);
        } 
        
        catch (Exception ex) {
            // If ANY error occurs inside the try block above, it is caught here.
            // Level.SEVERE = the highest error severity (something serious failed).
            // We log the error instead of crashing so the scanner remains usable.
            logger.log(java.util.logging.Level.SEVERE, "Error processing RFID time-" + logType.toLowerCase(), ex);
        }
    }

    // ── showTimedMessageDialog() ──────────────────────────────────────────────
    // PURPOSE: Show a popup message that automatically disappears after a set number of seconds.
    //
    // WHY NOT use JOptionPane.showMessageDialog() directly?
    //   Because the regular version BLOCKS the screen — the user must click OK to continue.
    //   This version uses a Timer to auto-close, so the RFID scanner stays immediately responsive.
    //
    // PARAMETERS:
    //   owner       → The parent window (the dialog will appear centered over it)
    //   message     → The text to show inside the popup
    //   title       → The text displayed in the popup's title bar
    //   messageType → The icon style: JOptionPane.INFORMATION_MESSAGE, WARNING_MESSAGE, etc.
    private static void showTimedMessageDialog(JFrame owner, String message, String title, int messageType) {
        // Create the popup's content panel using JOptionPane (the message + icon)
        JOptionPane pane = new JOptionPane(message, messageType);
        // Wrap it inside a JDialog window so we can programmatically close it
        JDialog dialog = pane.createDialog(owner, title);
        // setModal(false) = non-blocking: other windows remain usable while this dialog is open
        dialog.setModal(false);

        // CHANGE THE TIMER HERE ↓
        // Create a Timer that fires ONCE after 3000 milliseconds (3 seconds).
        // The lambda expression "e -> dialog.dispose()" is the action that runs when the timer fires:
        //   dialog.dispose() = close the dialog window and free its memory
        Timer timer = new Timer(3000, e -> dialog.dispose());
        timer.setRepeats(false); // Fire only ONCE — do not repeat every 3 seconds
        timer.start();           // Start the countdown (the timer runs in the background)

        // Make the dialog visible to the user — it will auto-close when the timer fires
        dialog.setVisible(true);
    }

    // ── sendGmailNotification() ───────────────────────────────────────────────
    // PURPOSE: Sends a plain-text email via Gmail's SMTP server.
    //
    // WHAT IS SMTP?
    //   SMTP (Simple Mail Transfer Protocol) is the internet standard for SENDING email.
    //   Gmail provides an SMTP server at smtp.gmail.com that programs can connect to.
    //
    // FLOW:
    //   Step 1 → Configure SMTP connection settings (server address, port, encryption)
    //   Step 2 → Create a Session (an authenticated connection to Gmail's SMTP server)
    //   Step 3 → Build the email (set From, To, Subject, Body)
    //   Step 4 → Deliver the email using Transport.send()
    //
    // PARAMETERS:
    //   toEmail → The recipient's email address (the parent's email)
    //   subject → The subject line of the email
    //   body    → The full plain-text content of the email
    public static void sendGmailNotification(String toEmail, String subject, String body) {
        // STEP 1: Configure the SMTP settings using a Properties object.
        // Properties works like a Map — you store configuration as key-value string pairs.
        Properties props = new Properties();
        props.put("mail.smtp.auth",           "true");          // Require login before sending
        props.put("mail.smtp.starttls.enable", "true");          // Upgrade connection to TLS (encrypted security)
        props.put("mail.smtp.host",            "smtp.gmail.com"); // Gmail's outgoing mail server address
        props.put("mail.smtp.port",            "587");            // Port 587 = standard TLS/STARTTLS email port

        // STEP 2: Create a mail Session using the settings above and our login credentials.
        // Session.getInstance() needs the Properties AND an Authenticator.
        // The Authenticator's getPasswordAuthentication() is called automatically
        // when Gmail's server requests "prove who you are" — it returns our credentials.
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // Provide our Gmail address and App Password to authenticate with Gmail
                return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });

        try {
            // STEP 3A: Create a new MimeMessage — the standard internet email format
            Message message = new MimeMessage(session);
            // Set the FROM address (who is sending this email)
            message.setFrom(new InternetAddress(EMAIL_FROM));
            // Set the TO address (who will receive this email)
            // InternetAddress.parse() converts the plain email string into an address object array
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            // Set the email's subject line
            message.setSubject(subject);
            // Set the plain-text body of the email
            message.setText(body);

            // STEP 4: Send the email through Gmail's SMTP server
            Transport.send(message);
            // Log a success message to the console confirming delivery
            logger.info("Email notification sent to " + toEmail + ": " + subject);
        } catch (MessagingException ex) {
            // If sending fails (wrong credentials, no internet, blocked port, etc.), log the error.
            // The application continues running — a failed email should NOT crash the scanner.
            logger.log(java.util.logging.Level.SEVERE, "Failed to send email notification", ex);
        }
    }
}
