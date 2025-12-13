package com.jjmc.chromashift.database;

import java.sql.*;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chromashift_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    
    /**
     * Get a connection to the XAMPP MySQL database
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }
    
    /**
     * Test connection to database
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("✓ Connected to: " + meta.getDatabaseProductName());
            System.out.println("✓ Version: " + meta.getDatabaseProductVersion());
            return true;
        } catch (SQLException e) {
            System.err.println("✗ Database connection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Close resources safely
     */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
