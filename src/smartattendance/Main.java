package smartattendance;

import smartattendance.store.AttendanceStore;
import smartattendance.store.FileAttendanceStore;
import smartattendance.store.JdbcAttendanceStore;
import smartattendance.web.AttendanceServer;
import smartattendance.web.TokenService;
import smartattendance.ui.UserTypeDialog;
import smartattendance.ui.LoginDialog;
import smartattendance.ui.AdminFrame;
import smartattendance.ui.TeacherHomeFrame;

import javax.swing.*;
import java.nio.file.Path;

public final class Main {
    private static AttendanceStore store;
    private static TokenService tokenService;
    private static AttendanceServer server;

    public static void main(String[] args) throws Exception {
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl != null && !dbUrl.isBlank()) {
            store = new JdbcAttendanceStore(dbUrl, System.getenv("DB_USER"), System.getenv("DB_PASS"));
        } else {
            store = new FileAttendanceStore(Path.of("data"));
        }
        store.initialize();

        tokenService = new TokenService();
        server = new AttendanceServer(8080, store, tokenService);
        server.start();
        tokenService.start();

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(Main::showUserSelection);
        }

        System.out.println("Web Server running for students at: " + server.getBaseUrl());
    }

    private static void showUserSelection() {
        UserTypeDialog typeDialog = new UserTypeDialog(null);
        typeDialog.setVisible(true);
        String type = typeDialog.getUserType();

        if (type != null) {
            LoginDialog login = new LoginDialog(null, store, type);
            login.setVisible(true);
            if (login.isAuthenticated()) {
                if ("ADMIN".equals(type)) {
                    new AdminFrame(store, Main::showUserSelection).setVisible(true);
                } else {
                    new TeacherHomeFrame(tokenService, store, server.getBaseUrl(), Main::showUserSelection).setVisible(true);
                }
            } else {
                showUserSelection();
            }
        } else {
            System.exit(0);
        }
    }
}
