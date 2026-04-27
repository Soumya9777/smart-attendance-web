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
import java.util.List;

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

        StringBuilder teacherRows = new StringBuilder();
        List<Teacher> teachers = store.findAllTeachers();
        for (Teacher t : teachers) {
            teacherRows.append("<tr><td>").append(AttendanceServer.escape(t.getId()))
                    .append("</td><td>").append(AttendanceServer.escape(t.getName()))
                    .append("</td><td><a href=\"/admin-delete-user?type=teacher&id=").append(t.getId())
                    .append("\" class=\"success\" style=\"color:#dc2626\">Delete</a></td></tr>");
        }

        StringBuilder studentRows = new StringBuilder();
        List<Student> students = store.findAllStudents();
        for (Student s : students) {
            studentRows.append("<tr><td>").append(AttendanceServer.escape(s.getId()))
                    .append("</td><td>").append(AttendanceServer.escape(s.getName()))
                    .append("</td><td><a href=\"/admin-delete-user?type=student&id=").append(s.getId())
                    .append("\" class=\"success\" style=\"color:#dc2626\">Delete</a></td></tr>");
        }

        StringBuilder subjectList = new StringBuilder();
        List<String> subjects = store.findAllSubjects();
        for (String sub : subjects) {
            subjectList.append("<span class=\"status present\" style=\"margin-right:8px; margin-bottom:8px; display:inline-block;\">")
                    .append(AttendanceServer.escape(sub)).append("</span>");
        }

        String body = "<div class=\"eyebrow\">Admin Dashboard</div>"
                + "<h1>System Management</h1>"
                + "<p><a class=\"button-link secondary\" href=\"/admin-logout\">Logout</a></p>"
                
                + "<div style=\"display:flex; gap:10px; margin-bottom:30px;\">"
                + "<button onclick=\"document.getElementById('teacherForm').style.display='block'; document.getElementById('studentForm').style.display='none';\" style=\"width:auto;\">+ Add Teacher</button>"
                + "<button onclick=\"document.getElementById('studentForm').style.display='block'; document.getElementById('teacherForm').style.display='none';\" style=\"width:auto;\">+ Add Student</button>"
                + "</div>"

                + "<div id=\"teacherForm\" style=\"display:none; margin-bottom:30px; padding:25px; background:rgba(255,255,255,0.7); border-radius:16px; border:1px solid var(--line); box-shadow: 0 10px 30px rgba(0,0,0,0.05);\">"
                + "<h2>Register New Teacher</h2>"
                + "<form method=\"post\" action=\"/admin-add-teacher\">"
                + "<label>Teacher ID<input name=\"id\" required></label>"
                + "<label>Full Name<input name=\"name\" required></label>"
                + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                + "<div style=\"display:flex; gap:10px;\">"
                + "<button type=\"submit\">Save Teacher</button>"
                + "<button type=\"button\" class=\"secondary\" onclick=\"document.getElementById('teacherForm').style.display='none';\">Cancel</button>"
                + "</div></form></div>"

                + "<div id=\"studentForm\" style=\"display:none; margin-bottom:30px; padding:25px; background:rgba(255,255,255,0.7); border-radius:16px; border:1px solid var(--line); box-shadow: 0 10px 30px rgba(0,0,0,0.05);\">"
                + "<h2>Register New Student</h2>"
                + "<form method=\"post\" action=\"/admin-add-student\">"
                + "<label>Student ID<input name=\"id\" required></label>"
                + "<label>Full Name<input name=\"name\" required></label>"
                + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                + "<div style=\"display:flex; gap:10px;\">"
                + "<button type=\"submit\">Save Student</button>"
                + "<button type=\"button\" class=\"secondary\" onclick=\"document.getElementById('studentForm').style.display='none';\">Cancel</button>"
                + "</div></form></div>"

                + "<h2>Manage Subjects</h2>"
                + "<div class=\"action-panel\">"
                + "<form method=\"post\" action=\"/admin-add-subject\" style=\"display:flex; gap:10px; width:100%; align-items:flex-end; margin:0;\">"
                + "<label style=\"margin:0; flex:1;\">New Subject Name<input name=\"subject\" required style=\"margin-top:4px;\"></label>"
                + "<button type=\"submit\" style=\"width:auto; padding:12px 20px;\">Add</button>"
                + "</form></div>"
                + "<div style=\"margin-top:10px; margin-bottom:40px;\">" + subjectList + "</div>"

                + "<div style=\"display:flex; gap:30px; flex-wrap:wrap;\">"
                + "<div style=\"flex:1; min-width:300px;\">"
                + "<h3>Registered Teachers</h3>"
                + "<table><thead><tr><th>ID</th><th>Name</th><th>Action</th></tr></thead><tbody>" + teacherRows + "</tbody></table>"
                + "</div>"
                + "<div style=\"flex:1; min-width:300px;\">"
                + "<h3>Registered Students</h3>"
                + "<table><thead><tr><th>ID</th><th>Name</th><th>Action</th></tr></thead><tbody>" + studentRows + "</tbody></table>"
                + "</div></div>";

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

    public void handleAddSubject(HttpExchange exchange) throws IOException {
        if (currentAdmin(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/admin-login");
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            try {
                store.saveSubject(form.get("subject"));
                AttendanceServer.redirect(exchange, "/admin-dashboard");
            } catch (Exception e) {
                AttendanceServer.sendHtml(exchange, 400, AttendanceServer.page("Error", "<h1>Failed</h1><p>" + AttendanceServer.escape(e.getMessage()) + "</p><a class=\"button-link\" href=\"/admin-dashboard\">Back</a>"));
            }
        }
    }

    public void handleDeleteUser(HttpExchange exchange) throws IOException {
        if (currentAdmin(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/admin-login");
            return;
        }
        Map<String, String> query = AttendanceServer.parseQuery(exchange.getRequestURI().getRawQuery());
        String type = query.get("type");
        String id = query.get("id");
        if (type != null && id != null) {
            if (type.equals("teacher")) {
                store.deleteTeacher(id);
            } else if (type.equals("student")) {
                store.deleteStudent(id);
            }
        }
        AttendanceServer.redirect(exchange, "/admin-dashboard");
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
