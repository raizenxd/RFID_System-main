                                                                                                                                                                                                                       /*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.ict.d_4;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ── ConnectXamppSQL CLASS ──────────────────────────────────────────────────────────────
// PURPOSE: A custom database helper class that wraps all MySQL operations (CRUD).
// CRUD stands for: Create (INSERT), Read (SELECT), Update (UPDATE), Delete (DELETE).
//
// WHY BUILD THIS CLASS?
//   Raw JDBC (Java Database Connectivity) code is verbose and repetitive.
//   This class wraps it in a "fluent builder" pattern so the rest of the app
//   can read/write the database using clean, readable method chains like:
//     ConnectXamppSQL.Insert("table").set("col", "val").execute();
//     ConnectXamppSQL.Read("table").where("col","=","val").getOne();
//
// WHAT IS JDBC?
//   JDBC is Java's standard API for connecting to relational databases (like MySQL).
//   It uses a "connection URL" to identify the server, database, and credentials.
//
// ⚠ SECURITY NOTE: This class builds SQL strings by directly concatenating user input.
//   This pattern is vulnerable to SQL Injection attacks (OWASP Top 10 risk).
//   In production, always use PreparedStatements with parameterized queries instead.
public class ConnectXamppSQL {
    // ── conn() ──────────────────────────────────────────────────────────────────
    // PURPOSE: Opens and returns a live JDBC connection to the MySQL database.
    //
    // HOW IT WORKS:
    //   DriverManager.getConnection() uses a JDBC URL to locate and connect to MySQL.
    //   URL format: jdbc:mysql://HOST:PORT/DATABASE_NAME
    //   - "localhost" = the database is on the SAME computer (XAMPP running locally)
    //   - "3306"      = MySQL's default port number
    //   - "root"      = the default XAMPP username
    //   - ""          = empty password (XAMPP default — no password set)
    //
    // "throws Exception" declares that this method can fail.
    // If XAMPP is not running or the database name is wrong, it throws an exception.
    // The calling code is responsible for catching it with try/catch.
    //
    // ⚠ NOTE: In a real production system, never use the root account with no password.
    //   Create a dedicated database user with only the minimum permissions required.
    public static Connection conn() throws Exception {
        String databaseName = "rfid verification"; // The MySQL database name (the space is intentional — must match exactly)
        // Build the connection URL and open a new database connection
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/"+databaseName, "root", "");
    }
    // ── FLUENT INSERT ───────────────────────────────────────────────────────────────
    // PURPOSE: Provides a readable "builder" pattern for SQL INSERT statements.
    //
    // WHAT IS THE BUILDER PATTERN?
    //   Instead of manually writing a full SQL string, you call method-by-method:
    //     ConnectXamppSQL.Insert("students_information")
    //         .set("RFID_Number", "12345")
    //         .set("Firstname",   "Juan")
    //         .execute();
    //   This is much easier to read, write, and maintain than raw SQL strings.
    //
    // This factory method creates and returns a new InsertBuilder for the given table.
    public static InsertBuilder Insert(String table) {
        return new InsertBuilder(table); // Create a fresh builder targeting this specific table
    }

    // InsertBuilder is a "static nested class" — it lives inside ConnectXamppSQL.
    // It collects column names and values, then assembles and runs the SQL on demand.
    public static class InsertBuilder {
        private String table;                             // The database table to insert into
        private List<String> columns = new ArrayList<>();  // Accumulates column names (e.g., "Firstname")
        private List<String> values  = new ArrayList<>();  // Accumulates matching values (e.g., "'Juan'")

        // Constructor: stores which table this INSERT will target
        public InsertBuilder(String table) {
            this.table = table;
        }

        // .set() adds one column=value pair to the INSERT.
        // The value gets wrapped in single quotes because SQL string literals require them.
        // Returns "this" (the same builder object) so multiple .set() calls can be chained.
        // ⚠ SECURITY NOTE: Value is directly concatenated — vulnerable to SQL Injection.
        //   In production, use PreparedStatement with ? placeholders instead.
        public InsertBuilder set(String column, String value) {
            columns.add(column);           // e.g., "Firstname"
            values.add("'" + value + "'"); // Wrap in SQL quotes: e.g., "'Juan'"
            return this;                   // Return self to allow chaining: .set(...).set(...)
        }

        // .execute() assembles the final SQL INSERT string and runs it against the database.
        // String.join(", ", list) joins all list items with ", " as the separator.
        // Example SQL produced:
        //   INSERT INTO students_information (RFID_Number, Firstname) VALUES ('12345', 'Juan')
        public void execute() throws Exception {
            String sql = "INSERT INTO " + table
                    + " (" + String.join(", ", columns) + ")"      // Column list
                    + " VALUES (" + String.join(", ", values) + ")"; // Values list
            conn().createStatement().execute(sql); // Open connection, create statement, run it
            System.out.println("Inserted: " + sql); // Debug: print the SQL to the console
        }
    }
    // ── FLUENT UPDATE ───────────────────────────────────────────────────────────────
    // PURPOSE: Provides a readable builder pattern for SQL UPDATE statements.
    //
    // Usage example:
    //   ConnectXamppSQL.Update("students_information")
    //       .set("firstname", "Pedro")
    //       .where("rfid_number", "=", "12345")
    //       .execute();
    //
    // The SQL it builds: UPDATE students_information SET firstname='Pedro' WHERE rfid_number = '12345'
    public static UpdateBuilder Update(String table) {
        return new UpdateBuilder(table); // Create a fresh builder targeting the given table
    }

    public static class UpdateBuilder {
        private String table;                                  // The table to update
        private List<String> setClauses   = new ArrayList<>(); // Collects "column='value'" pairs for SET
        private List<String> whereClauses = new ArrayList<>(); // Collects filter conditions for WHERE

        public UpdateBuilder(String table) {
            this.table = table;
        }

        // .set() adds a "column = 'value'" clause to the SET portion of the UPDATE statement.
        // ⚠ SECURITY NOTE: Direct string concatenation — vulnerable to SQL Injection in production.
        public UpdateBuilder set(String column, String value) {
            setClauses.add(column + "='" + value + "'"); // e.g., "firstname='Pedro'"
            return this; // Return self for method chaining
        }

        // .where() adds a filter condition to the WHERE clause.
        // Supports a shorthand: passing "%" as the operator automatically switches to LIKE.
        // If using LIKE without wildcards, it wraps the value: "value" → "%value%" ("contains" match).
        public UpdateBuilder where(String column, String op, String value) {
            if (op.equals("%")) op = "LIKE";  // "%" is treated as shorthand for the LIKE operator
            if (op.equalsIgnoreCase("LIKE") && !value.contains("%")) value = "%" + value + "%"; // Auto-add wildcard
            whereClauses.add(column + " " + op + " '" + value + "'"); // e.g., "rfid_number = '12345'"
            return this; // Return self for chaining
        }

        // .execute() assembles and runs the full UPDATE SQL statement.
        // ⚠ WARNING: If no .where() is called, ALL rows in the table will be updated!
        //   Always add a WHERE condition to target specific rows.
        public void execute() throws Exception {
            // Build the base: UPDATE tableName SET col1='val1', col2='val2'
            String sql = "UPDATE " + table + " SET " + String.join(", ", setClauses);
            // Append the WHERE clause only if at least one condition was added
            if (!whereClauses.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", whereClauses); // AND joins multiple conditions
            }
            conn().createStatement().execute(sql); // Execute the SQL UPDATE statement
            System.out.println("Updated: " + sql); // Debug: print to console
        }
    }
    // ── FLUENT DELETE ───────────────────────────────────────────────────────────────
    // PURPOSE: Deletes rows from a table that match a given condition.
    //
    // Usage example:
    //   ConnectXamppSQL.Delete("students_information", "rfid_number", "=", "12345");
    //
    // The SQL it builds: DELETE FROM students_information WHERE rfid_number = '12345'
    //
    // PARAMETERS:
    //   table  → Which table to delete from (e.g., "students_information")
    //   column → The column to use as the filter (e.g., "rfid_number")
    //   op     → Comparison operator: "=", "LIKE", or "%" (shorthand for LIKE)
    //   value  → The value to match (e.g., "12345")
    //
    // ⚠ CAUTION: Deleting a student record is permanent and cannot be undone.
    //   Always confirm with the user before calling this method.
    // ⚠ SECURITY NOTE: Direct string concatenation — vulnerable to SQL Injection.
    public static void Delete(String table, String column, String op, String value) throws Exception {
        if (op.equals("%")) op = "LIKE";  // Accept "%" as shorthand for the LIKE operator
        if (op.equalsIgnoreCase("LIKE") && !value.contains("%")) value = "%" + value + "%"; // Auto-add wildcards
        // Build: DELETE FROM tableName WHERE column op 'value'
        String sql = "DELETE FROM " + table + " WHERE " + column + " " + op + " '" + value + "'";
        conn().createStatement().execute(sql); // Execute the DELETE statement
        System.out.println("Deleted: " + sql); // Debug: print to console
    }
    // ── FLUENT READ ────────────────────────────────────────────────────────────────
    // PURPOSE: Provides a readable builder pattern for SQL SELECT queries.
    //
    // Usage examples:
    //   ConnectXamppSQL.Read("students_information").get();                             // All rows
    //   ConnectXamppSQL.Read("students_information").where("rfid_number","=","123").getOne(); // One row
    //
    // HOW IT WORKS:
    //   Read() returns an INSTANCE (object) of ConnectXamppSQL itself.
    //   That instance stores the table name and any WHERE conditions you add.
    //   Finally, .get() or .getOne() runs the query and returns results.
    //
    // These are instance fields (not static) — each Read() call gets its own copy:
    private String table;                                  // The target table for the SELECT query
    private List<String> conditions = new ArrayList<>();   // Stores WHERE filter conditions

    // Factory method: creates a new ConnectXamppSQL instance configured for the given table.
    // "static" so you can call it without creating an object: ConnectXamppSQL.Read("table")
    public static ConnectXamppSQL Read(String table) {
        ConnectXamppSQL r = new ConnectXamppSQL(); // Create a fresh instance
        r.table = table;                           // Set which table to query
        return r;                                  // Return it for method chaining
    }

    // .where() adds one filter condition to the SELECT query.
    // Each call adds an additional condition; they are joined with AND in the final SQL.
    // Shorthand: passing "%" as op automatically uses LIKE with wildcard matching.
    // ⚠ SECURITY NOTE: Direct string concatenation — vulnerable to SQL Injection.
    public ConnectXamppSQL where(String column, String op, String value) {
        if (op.equals("%")) op = "LIKE";  // "%" is shorthand for the LIKE operator
        if (op.equalsIgnoreCase("LIKE") && !value.contains("%")) value = "%" + value + "%"; // Auto-add wildcards
        conditions.add(column + " " + op + " '" + value + "'"); // e.g., "rfid_number = '123'"
        return this; // Return self to allow chaining: .where(...).where(...)
    }

    // .get() runs the SELECT query and returns ALL matching rows.
    // Each row is returned as a Map<String, String>:
    //   Key   = column name  (e.g., "rfid_number")
    //   Value = cell value   (e.g., "12345")
    // The full result is a List of these Maps (one Map per database row).
    public List<Map<String,String>> get() throws Exception {
        List<Map<String,String>> rows = new ArrayList<>(); // Will hold all result rows

        // Build the WHERE clause string from all conditions that were added via .where()
        String whereClause = "";
        if (!conditions.isEmpty()) {
            whereClause = " WHERE " + String.join(" AND ", conditions); // e.g., " WHERE rfid_number = '123'"
        }

        // Execute the SELECT query: SELECT * FROM tableName [WHERE ...]
        // SELECT * means "get all columns" from the table
        ResultSet rs = conn().createStatement().executeQuery(
            "SELECT * FROM " + table + whereClause
        );

        // ResultSetMetaData gives us information ABOUT the result structure
        // (e.g., how many columns are there? what are their names?)
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount(); // Total number of columns in this table

        // rs.next() advances to the next row in the result set.
        // It returns true while there are more rows, false when we've read them all.
        while (rs.next()) {
            Map<String,String> row = new HashMap<>(); // One Map will hold all cells of this row

            // Loop through every column in the current row.
            // JDBC columns are 1-indexed (start at 1, not 0 like Java arrays).
            for (int i = 1; i <= colCount; i++) {
                String colName = md.getColumnName(i); // Get the column's name (e.g., "rfid_number")
                String value   = rs.getString(i);     // Get the cell's value as a String
                row.put(colName, value);               // Store: {"rfid_number" → "12345"}
                System.out.print(colName + ": " + value + " | "); // Debug: print each cell
            }
            System.out.println(); // Move to a new console line after printing each full row
            rows.add(row);        // Add this complete row Map to the results list
        }
        return rows; // Return all collected rows
    }

    // .getOne() is a convenience wrapper around .get() that returns only the FIRST row.
    // Use this when you expect exactly one result (e.g., looking up one student by RFID).
    // Returns null if no matching rows were found — always check for null before using the result!
    public Map<String,String> getOne() throws Exception {
        List<Map<String,String>> rows = get(); // Run the full query via .get()
        if (rows.isEmpty()) return null;       // No results found — return null
        return rows.get(0);                    // Return only the first row (index 0)
    }
}