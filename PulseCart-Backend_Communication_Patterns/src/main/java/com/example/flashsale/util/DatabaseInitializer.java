//package com.example.flashsale.util;
//
//import org.springframework.context.annotation.Configuration;
//
//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.SQLException;
//import java.sql.Statement;
//
//@Configuration
//public class DatabaseInitializer {
//
//    public DatabaseInitializer() {
//        createDatabaseIfNotExists();
//    }
//
//    private void createDatabaseIfNotExists() {
//        String url = "jdbc:postgresql://localhost:5432/ecommerce";
//        String user = "ecommerce";
//        String password = "root";
//
//try (Connection conn = DriverManager.getConnection(url, user, password);
//     Statement stmt = conn.createStatement()) {
//    stmt.executeUpdate("CREATE DATABASE ecommerce");
//    System.out.println("Database 'ecommerce' created successfully.");
//
//} catch (SQLException e) {
//    if ("42P04".equals(e.getSQLState())) {
//
//
//
//                    // "42P04" is the SQL state code for "database already exists"
//                    System.out.println("Database already exists. Skipping creation.");
//                } else {
//                    System.err.println("Failed to create database due to SQL error: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            }
//        } catch (SQLException e) {
//        System.err.println("Connection or statement error: " + e.getMessage());
//        e.printStackTrace();
//
//            e.printStackTrace();
//        }
//    }
//}
