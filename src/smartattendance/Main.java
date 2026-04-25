package smartattendance;

import smartattendance.store.FileAttendanceStore;
import smartattendance.web.AttendanceServer;
import smartattendance.web.TokenService;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Smart Attendance headless server...");

        Path dataDirectory = Path.of("data");
        FileAttendanceStore store = new FileAttendanceStore(dataDirectory);
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
