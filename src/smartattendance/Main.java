package smartattendance;

import smartattendance.store.AttendanceStore;
import smartattendance.store.FileAttendanceStore;
import smartattendance.store.JdbcAttendanceStore;
import smartattendance.web.AttendanceServer;
import smartattendance.web.TokenService;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Smart Attendance Management System...");

        AttendanceStore store;
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl != null && !dbUrl.isBlank()) {
            System.out.println("Connecting to database: " + dbUrl);
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASS");
            
            // Register MySQL Driver
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.err.println("Warning: MySQL JDBC Driver not found in classpath.");
            }
            
            store = new JdbcAttendanceStore(dbUrl, dbUser, dbPass);
        } else {
            System.out.println("Using file-based storage (CSV). Set DB_URL to switch to MySQL.");
            store = new FileAttendanceStore(Path.of("data"));
        }
        
        store.initialize();

        TokenService tokenService = new TokenService();
        
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        AttendanceServer server = new AttendanceServer(port, store, tokenService);
        server.start();
        tokenService.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tokenService.stop();
            server.stop();
        }));

        System.out.println("Server is running on " + server.getBaseUrl());
        System.out.println("Press Ctrl+C to stop.");
    }
}
