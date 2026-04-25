package smartattendance.web;

import com.sun.net.httpserver.HttpExchange;
import smartattendance.model.Admin;
import smartattendance.model.Student;
import smartattendance.model.Teacher;
import smartattendance.store.AttendanceStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AdminWebHandler {
    private final AttendanceStore store;
    private final Map<String, String> adminSessions = new ConcurrentHashMap<>();

    public AdminWebHandler(AttendanceStore store) {
        this.store = store;
    }

    public void handleLogin(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String form = "<div class=\"eyebrow\">Admin Access</div>"
                    + "<h1>Admin Login</h1>"
                    + "<form method=\"post\" action=\"/admin-login\">"
                    + "<label>Admin ID<input name=\"adminId\" required autofocus></label>"
                    + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                    + "<button type=\"submit\">Login</button>"
                    + "</form>";
            AttendanceServer.sendHtml(exchange, 200, AttendanceServer.page("Admin Login", form));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            Optional<Admin> optionalAdmin = store.findAdminById(form.getOrDefault("adminId", ""));

            if (optionalAdmin.isEmpty() || !optionalAdmin.get().passwordMatches(form.getOrDefault("password", ""))) {
                AttendanceServer.sendHtml(exchange, 401, AttendanceServer.page("Login Failed",
                        "<h1>Invalid admin ID or password</h1><p><a class=\"button-link\" href=\"/admin-login\">Try Again</a></p>"));
                return;
            }

            String sessionId = java.util.UUID.randomUUID().toString();
            adminSessions.put(sessionId, optionalAdmin.get().getId());
            exchange.getResponseHeaders().add("Set-Cookie", "ADMIN_SESSION=" + sessionId + "; Path=/; HttpOnly");
            AttendanceServer.redirect(exchange, "/admin-dashboard");
        }
    }

    public void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId != null) {
            adminSessions.remove(sessionId);
        }
        exchange.getResponseHeaders().add("Set-Cookie", "ADMIN_SESSION=; Path=/; Max-Age=0; HttpOnly");
        AttendanceServer.redirect(exchange, "/admin-login");
    }

    public void handleDashboard(HttpExchange exchange) throws IOException {
        if (currentAdmin(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/admin-login");
            return;
        }

        String body = "<div class=\"eyebrow\">Admin Dashboard</div>"
                + "<h1>System Management</h1>"
                + "<p><a class=\"button-link secondary\" href=\"/admin-logout\">Logout</a></p>"
                + "<div style=\"display:flex; gap:20px; flex-wrap:wrap; margin-top:30px;\">"
                + "<div style=\"flex:1; min-width:300px;\"><h2>Register Teacher</h2>"
                + "<form method=\"post\" action=\"/admin-add-teacher\">"
                + "<label>Teacher ID<input name=\"id\" required></label>"
                + "<label>Full Name<input name=\"name\" required></label>"
                + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                + "<button type=\"submit\">Add Teacher</button>"
                + "</form></div>"
                + "<div style=\"flex:1; min-width:300px;\"><h2>Register Student</h2>"
                + "<form method=\"post\" action=\"/admin-add-student\">"
                + "<label>Student ID<input name=\"id\" required></label>"
                + "<label>Full Name<input name=\"name\" required></label>"
                + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                + "<button type=\"submit\">Add Student</button>"
                + "</form></div></div>";

        AttendanceServer.sendHtml(exchange, 200, AttendanceServer.page("Admin Dashboard", body));
    }

    public void handleAddTeacher(HttpExchange exchange) throws IOException {
        if (currentAdmin(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/admin-login");
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            try {
                store.saveTeacher(new Teacher(form.get("id"), form.get("name"), form.get("password")));
                AttendanceServer.redirect(exchange, "/admin-dashboard");
            } catch (Exception e) {
                AttendanceServer.sendHtml(exchange, 400, AttendanceServer.page("Error", "<h1>Failed</h1><p>" + AttendanceServer.escape(e.getMessage()) + "</p><a class=\"button-link\" href=\"/admin-dashboard\">Back</a>"));
            }
        }
    }

    public void handleAddStudent(HttpExchange exchange) throws IOException {
        if (currentAdmin(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/admin-login");
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            try {
                store.saveStudent(new Student(form.get("id"), form.get("name"), form.get("password")));
                AttendanceServer.redirect(exchange, "/admin-dashboard");
            } catch (Exception e) {
                AttendanceServer.sendHtml(exchange, 400, AttendanceServer.page("Error", "<h1>Failed</h1><p>" + AttendanceServer.escape(e.getMessage()) + "</p><a class=\"button-link\" href=\"/admin-dashboard\">Back</a>"));
            }
        }
    }

    private Optional<Admin> currentAdmin(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId == null) return Optional.empty();
        String adminId = adminSessions.get(sessionId);
        return adminId != null ? store.findAdminById(adminId) : Optional.empty();
    }

    private String currentSessionId(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "ADMIN_SESSION".equals(parts[0])) return parts[1];
        }
        return null;
    }
}
