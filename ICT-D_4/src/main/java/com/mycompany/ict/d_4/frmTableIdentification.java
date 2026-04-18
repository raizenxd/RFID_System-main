/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.mycompany.ict.d_4;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
/**
 *
 * @author pc
 */
public class frmTableIdentification extends javax.swing.JFrame {
    
    // Logger: records errors to the console with severity levels (INFO, WARNING, SEVERE).
    // Using a Logger is more professional than System.out.println and easier to manage in large apps.
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(frmTableIdentification.class.getName());

    // DB_TIMESTAMP_FORMAT matches the exact format MySQL stores timestamps in: "2026-04-18 08:30:00"
    // Used to PARSE (convert) the raw timestamp string from the database into a LocalDateTime object.
    // "HH" = 24-hour clock (needed to correctly parse database timestamps like "14:30:00").
    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // TIME_FORMAT: formats a LocalDateTime into a 12-hour time string with AM/PM, e.g., "02:30 PM"
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

    // DATE_FORMAT: formats a LocalDateTime into just the date, e.g., "2026-04-18"
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Creates new form frmTableIdentification
     */
    public frmTableIdentification() {
        initComponents();        // NetBeans-generated: builds and wires all GUI components (table, buttons, labels)
        loadAttendanceTable(""); // Immediately populate the table with ALL students when the window first opens
                                 // Passing "" (empty string) = no filter — show every student
    }

    // ── loadAttendanceTable() ────────────────────────────────────────────────────
    // PURPOSE: Fills the tbIdentification table on screen with student attendance data.
    //
    // PARAMETER:
    //   rfidFilter → If empty (""), show ALL students.
    //                If a specific RFID string is passed, show only that student's records.
    //
    // OVERVIEW OF STEPS:
    //   1. Clear the table so we start fresh
    //   2. Fetch all students from the database into a quick-lookup Map (indexed by RFID)
    //   3. Fetch attendance logs (optionally filtered by RFID)
    //   4. For each log, look up the student's details and add a row to the table
    //   5. If showing ALL students (no filter), also list students who have no logs yet
    //   6. If filtered but that student has no logs, still show them with blank time columns
    private void loadAttendanceTable(String rfidFilter) {
        try {
            // STEP 1: Get the table's data model and clear all existing rows.
            // "var" is a Java 10+ shorthand — the compiler infers the type automatically.
            // DefaultTableModel is the object that holds the table's actual row data.
            // .setRowCount(0) is the most efficient way to delete all rows at once.
            var m = (javax.swing.table.DefaultTableModel) tbIdentification.getModel();
            m.setRowCount(0); // Wipe all rows — start fresh before reloading data

            // STEP 2A: Fetch every student from the students_information table.
            // Returns a List of Maps; each Map represents one row (column name → value).
            List<Map<String, String>> students = ConnectXamppSQL.Read("students_information").get();

            // STEP 2B: Build an "index" Map for fast student lookup by RFID.
            // Key = rfid_number string, Value = the full student Map.
            // This avoids searching the entire students list every time we process a log.
            java.util.Map<String, Map<String, String>> studentByRfid = new java.util.HashMap<>();
            for (Map<String, String> student : students) {
                String rfid = student.getOrDefault("rfid_number", "");
                if (!rfid.isBlank()) {
                    studentByRfid.put(rfid, student); // Map RFID → student info for instant lookup
                }
            }

            // STEP 3A: Prepare a SELECT query for attendance_logs.
            ConnectXamppSQL logsQuery = ConnectXamppSQL.Read("attendance_logs");
            // STEP 3B: If a specific RFID was provided as a filter, add a WHERE condition.
            if (rfidFilter != null && !rfidFilter.isBlank()) {
                logsQuery = logsQuery.where("rfid_number", "=", rfidFilter); // Only load logs for this RFID
            }

            // STEP 3C: Execute the query and retrieve all matching log records.
            List<Map<String, String>> logs = logsQuery.get();

            // STEP 4: Process each log entry and add a row to the table.
            // loggedRfids tracks which RFIDs already appear in the table (used in Step 5).
            java.util.Set<String> loggedRfids = new java.util.HashSet<>();
            for (Map<String, String> log : logs) {
                String rfid = log.getOrDefault("rfid_number", "");
                loggedRfids.add(rfid); // Mark this RFID as "already has a row in the table"

                // Look up the student's name/section using the RFID as the key.
                // If the RFID isn't in studentByRfid (unregistered card), return an empty Map.
                Map<String, String> student = studentByRfid.getOrDefault(rfid, new java.util.HashMap<>());
                String firstname = student.getOrDefault("firstname", ""); // Student's first name
                String lastname  = student.getOrDefault("lastname",  ""); // Student's last name
                String section   = student.getOrDefault("section",   ""); // Student's section

                // Parse the log's timestamp into date and time parts for separate columns.
                String timestamp = log.getOrDefault("timestamp", ""); // e.g., "2026-04-18 08:30:00"
                String day  = ""; // Will store just the date: e.g., "2026-04-18"
                String time = ""; // Will store just the time: e.g., "08:30 AM"
                if (!timestamp.isBlank()) {
                    try {
                        // Convert the string timestamp into a LocalDateTime object we can format
                        LocalDateTime dateTime = LocalDateTime.parse(timestamp, DB_TIMESTAMP_FORMAT);
                        day  = dateTime.format(DATE_FORMAT); // Extract the date part
                        time = dateTime.format(TIME_FORMAT); // Extract the time part
                    } catch (Exception ex) {
                        day = timestamp; // If parsing fails, show the raw value rather than crashing
                    }
                }

                // Determine if this log is Time-In or Time-Out, and put the time in the right column.
                // The ternary operator: condition ? value_if_true : value_if_false
                // If log_type is "IN"  → put the formatted time in timeIn,  leave timeOut blank
                // If log_type is "OUT" → put the formatted time in timeOut, leave timeIn blank
                String timeIn  = "IN".equalsIgnoreCase(log.getOrDefault("log_type",  "")) ? time : "";
                String timeOut = "OUT".equalsIgnoreCase(log.getOrDefault("log_type", "")) ? time : "";

                // Add a new row to the table. The 7 values match the 7 column headers:
                // RFID_NUMBER | First Name | Last Name | Section | Time-In | Time-Out | Day
                m.addRow(new Object[]{rfid, firstname, lastname, section, timeIn, timeOut, day});
            }

            // STEP 5: When showing ALL students (no filter), include students who have no logs.
            // Without this step, registered students who haven't scanned today wouldn't appear.
            if (rfidFilter == null || rfidFilter.isBlank()) {
                for (Map<String, String> student : students) {
                    String rfid = student.getOrDefault("rfid_number", "");
                    // Skip students with no RFID, or those already in the table from logs above
                    if (rfid.isBlank() || loggedRfids.contains(rfid)) {
                        continue; // Jump to the next iteration (skip this student)
                    }
                    // Add the student with empty Time-In, Time-Out, and Day columns
                    m.addRow(new Object[]{rfid,
                            student.getOrDefault("firstname", ""),
                            student.getOrDefault("lastname",  ""),
                            student.getOrDefault("section",   ""),
                            "", "", ""});  // No attendance data yet for this student
                }
            } 
            
            // STEP 6: If a specific RFID was searched but that student has no logs,
            // still display the student's row — just with blank time columns.
            else if (!loggedRfids.contains(rfidFilter) && studentByRfid.containsKey(rfidFilter)) {
                Map<String, String> student = studentByRfid.get(rfidFilter); // Get the student's info
                m.addRow(new Object[]{rfidFilter,
                        student.getOrDefault("firstname", ""),
                        student.getOrDefault("lastname",  ""),
                        student.getOrDefault("section",   ""),
                        "", "", ""}); // Student exists but has not scanned today
            }
        } 
        
        catch (Exception e) {
            // If any database error occurs, log it. The table will remain empty/unchanged.
            logger.log(java.util.logging.Level.SEVERE, "Error loading attendance table", e);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        txtSearch = new javax.swing.JTextField();
        btnIdentification = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tbIdentification = new javax.swing.JTable();
        txtRFID = new javax.swing.JTextField();
        txtfirstname = new javax.swing.JTextField();
        txtlastname = new javax.swing.JTextField();
        txtSection = new javax.swing.JTextField();
        btnUpdate = new javax.swing.JButton();
        btnDel = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        txtSearch.addActionListener(this::txtSearchActionPerformed);
        getContentPane().add(txtSearch, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 90, 217, 40));

        btnIdentification.setText("Search Identification");
        btnIdentification.addActionListener(this::btnIdentificationActionPerformed);
        getContentPane().add(btnIdentification, new org.netbeans.lib.awtextra.AbsoluteConstraints(270, 90, 187, 40));

        tbIdentification.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null}
            },
            new String [] {
                "RFID_NUMBER", "First Name", "Last Name", "Section", "Time-In", "Time-Out", "Day"
            }
        ));
        tbIdentification.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tbIdentificationMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(tbIdentification);

        getContentPane().add(jScrollPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 160, 550, -1));

        txtRFID.addActionListener(this::txtRFIDActionPerformed);
        getContentPane().add(txtRFID, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 182, 250, 40));
        getContentPane().add(txtfirstname, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 230, 250, 40));

        txtlastname.addActionListener(this::txtlastnameActionPerformed);
        getContentPane().add(txtlastname, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 280, 250, 40));

        txtSection.addActionListener(this::txtSectionActionPerformed);
        getContentPane().add(txtSection, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 330, 250, 40));

        btnUpdate.setText("UPDATE");
        btnUpdate.addActionListener(this::btnUpdateActionPerformed);
        getContentPane().add(btnUpdate, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 460, 251, 56));

        btnDel.setText("DELETE");
        btnDel.addActionListener(this::btnDelActionPerformed);
        getContentPane().add(btnDel, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 520, 251, 56));

        jLabel2.setIcon(new javax.swing.ImageIcon("C:\\Users\\pc\\Documents\\Group 4 Figma\\TableIdentification background.png")); // NOI18N
        jLabel2.setText("jLabel2");
        getContentPane().add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 870, 600));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnIdentificationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIdentificationActionPerformed
        try {
            // Read what the admin typed into the search box and remove extra surrounding spaces.
            String search = txtSearch.getText().trim();
            // Reload the table using the search value as an RFID filter.
            // If search is blank, all students are shown; if it contains an RFID, only that student appears.
            loadAttendanceTable(search);
        } catch (Exception e) {
            // If the database query fails, log the error and show a user-friendly error popup.
            logger.log(java.util.logging.Level.SEVERE, "Error loading identification results", e);
            javax.swing.JOptionPane.showMessageDialog(this, "Unable to load attendance data: " + e.getMessage(), "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnIdentificationActionPerformed

    private void txtSearchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtSearchActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtSearchActionPerformed

    private void tbIdentificationMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tbIdentificationMouseClicked
        // Get the index of the row the admin clicked.
        // .getSelectedRow() returns -1 if nothing is selected, or a 0-based row number.
        int row = tbIdentification.getSelectedRow();
        // Safety check: if no row is selected (row is -1), do nothing and exit.
        if (row < 0) {
            return; // Exit early — nothing was selected
        }

        // Populate the text fields on the right side with data from the clicked row.
        // .getValueAt(rowIndex, columnIndex) gets the cell at that position (0-based columns).
        // Column 0 = RFID_NUMBER | 1 = First Name | 2 = Last Name | 3 = Section
        // .toString() converts the cell value (which is an Object type) into a plain String.
        txtRFID.setText(tbIdentification.getValueAt(row, 0).toString());      // Fill the RFID field
        txtfirstname.setText(tbIdentification.getValueAt(row, 1).toString()); // Fill First Name field
        txtlastname.setText(tbIdentification.getValueAt(row, 2).toString());  // Fill Last Name field
        txtSection.setText(tbIdentification.getValueAt(row, 3).toString());   // Fill Section field
        // After this, the admin can edit the text fields and click UPDATE or DELETE.
    }//GEN-LAST:event_tbIdentificationMouseClicked

    private void btnUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUpdateActionPerformed
        // STEP 1: Read the values currently in the text fields.
        // These were filled automatically when the admin clicked a table row,
        // and may have been manually edited before clicking UPDATE.
        String RFID_Number = txtRFID.getText();      // The RFID identifying which student to update
        String Firstname   = txtfirstname.getText(); // The new (or unchanged) first name
        String Lastname    = txtlastname.getText();  // The new (or unchanged) last name
        String Section     = txtSection.getText();   // The new (or unchanged) section
     
     
     try{
         // STEP 2: Run an UPDATE SQL query to modify the student's record.
         // .set() specifies which columns to change and their new values.
         // .where() targets ONLY the student with the matching RFID number.
         // Without .where(), ALL students in the table would be updated — so it is essential!
         ConnectXamppSQL.Update("students_information")
             .set("firstname", Firstname)             // Update the firstname column
             .set("lastname",  Lastname)              // Update the lastname column
             .set("section",   Section)               // Update the section column
             .where("rfid_number", "=", RFID_Number)  // Only update the row with this RFID
             .execute();                              // Run the SQL UPDATE statement
         
         // STEP 3: Notify the admin that the update was saved successfully.
         javax.swing.JOptionPane.showMessageDialog(null, "Information is been updated!");
         // STEP 4: Navigate to the Time-Out window (next screen in the workflow).
         new frmTimeOut().setVisible(true);
         // STEP 5: Close this window since the task is done.
         this.dispose();
     }
     
     catch(Exception e){
         // If the database update fails, this block runs.
         // Currently empty — consider adding an error popup here for better user feedback.
     }
   
            
    }//GEN-LAST:event_btnUpdateActionPerformed

    private void txtlastnameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtlastnameActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtlastnameActionPerformed

    private void txtSectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtSectionActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtSectionActionPerformed

    private void btnDelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDelActionPerformed
        // Get the RFID of the student selected in the table (auto-filled when a row was clicked).
        // This value identifies WHICH student's record will be permanently deleted.
        String RFID_Number = txtRFID.getText();
         
         try{
             // Run a DELETE SQL query to remove this student from the database.
             // ConnectXamppSQL.Delete() builds and runs: DELETE FROM students_information WHERE rfid_number = 'RFID_Number'
             // ⚠ WARNING: This is permanent. The student's record CANNOT be recovered after deletion.
             ConnectXamppSQL.Delete("students_information", "rfid_number", "=", RFID_Number);
             // Notify the admin that the deletion completed successfully.
             javax.swing.JOptionPane.showMessageDialog(null, "Information is been deleted!");
         }
         
         catch(Exception e){
             // If the DELETE fails (e.g., database error), this block runs.
             // Currently empty — consider adding an error popup for better user feedback.
         }
    }//GEN-LAST:event_btnDelActionPerformed

    private void txtRFIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtRFIDActionPerformed
   
    }//GEN-LAST:event_txtRFIDActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new frmTableIdentification().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnDel;
    private javax.swing.JButton btnIdentification;
    private javax.swing.JButton btnUpdate;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable tbIdentification;
    private javax.swing.JTextField txtRFID;
    private javax.swing.JTextField txtSearch;
    private javax.swing.JTextField txtSection;
    private javax.swing.JTextField txtfirstname;
    private javax.swing.JTextField txtlastname;
    // End of variables declaration//GEN-END:variables
}