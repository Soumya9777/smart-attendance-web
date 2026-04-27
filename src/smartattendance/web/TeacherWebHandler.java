package smartattendance.web;

import com.sun.net.httpserver.HttpExchange;
import smartattendance.model.Teacher;
import smartattendance.model.AttendanceRecord;
import smartattendance.model.ClassSession;
import smartattendance.store.AttendanceStore;
import smartattendance.qr.QrCodeGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TeacherWebHandler {
    private final AttendanceStore store;
    private final TokenService tokenService;
    private final Map<String, String> teacherSessions = new ConcurrentHashMap<>();

    public TeacherWebHandler(AttendanceStore store, TokenService tokenService) {
        this.store = store;
        this.tokenService = tokenService;
    }

    public void handleLogin(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String form = "<div class=\"eyebrow\">Teacher Access</div>"
                    + "<h1>Teacher Login</h1>"
                    + "<form method=\"post\" action=\"/teacher-login\">"
                    + "<label>Teacher ID<input name=\"teacherId\" required autofocus></label>"
                    + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                    + "<button type=\"submit\">Login</button>"
                    + "</form>";
            AttendanceServer.sendHtml(exchange, 200, AttendanceServer.page("Teacher Login", form));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            Optional<Teacher> optionalTeacher = store.findTeacherById(form.getOrDefault("teacherId", ""));

            if (optionalTeacher.isEmpty() || !optionalTeacher.get().passwordMatches(form.getOrDefault("password", ""))) {
                AttendanceServer.sendHtml(exchange, 401, AttendanceServer.page("Login Failed",
                        "<h1>Invalid teacher ID or password</h1><p><a class=\"button-link\" href=\"/teacher-login\">Try Again</a></p>"));
                return;
            }

            String sessionId = java.util.UUID.randomUUID().toString();
            teacherSessions.put(sessionId, optionalTeacher.get().getId());
            exchange.getResponseHeaders().add("Set-Cookie", "TEACHER_SESSION=" + sessionId + "; Path=/; HttpOnly");
            AttendanceServer.redirect(exchange, "/teacher-dashboard");
        }
    }

    public void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId != null) {
            teacherSessions.remove(sessionId);
        }
        exchange.getResponseHeaders().add("Set-Cookie", "TEACHER_SESSION=; Path=/; Max-Age=0; HttpOnly");
        AttendanceServer.redirect(exchange, "/teacher-login");
    }

    public void handleDashboard(HttpExchange exchange) throws IOException {
        Optional<Teacher> optionalTeacher = currentTeacher(exchange);
        if (optionalTeacher.isEmpty()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }
        Teacher teacher = optionalTeacher.get();

        StringBuilder content = new StringBuilder();
        content.append("<div class=\"eyebrow\">Teacher Portal</div>");
        content.append("<div style=\"display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;\">");
        content.append("<h1>Welcome, ").append(AttendanceServer.escape(teacher.getName())).append("</h1>");
        content.append("<div><a class=\"button-link\" href=\"/teacher-history\" style=\"margin-right:10px;\">View History</a>");
        content.append("<a class=\"button-link secondary\" href=\"/teacher-logout\">Logout</a></div></div>");

        if (!tokenService.isSessionActive()) {
            // Show Start Session Form
            StringBuilder subjectOptions = new StringBuilder();
            for (String sub : store.findAllSubjects()) {
                subjectOptions.append("<option value=\"").append(AttendanceServer.escape(sub)).append("\">")
                        .append(AttendanceServer.escape(sub)).append("</option>");
            }

            content.append("<div class=\"action-panel\">")
                    .append("<h2>Start New Attendance Session</h2>")
                    .append("<form method=\"post\" action=\"/teacher-start-session\">")
                    .append("<label>Class Name (e.g. CS-2024)<input name=\"className\" required></label>")
                    .append("<label>Subject<select name=\"subject\" style=\"width:100%; padding:14px; border-radius:12px; border:1px solid #d1d5db; background:#f9fafb;\">").append(subjectOptions).append("</select></label>")
                    .append("<label>Topic<input name=\"topic\" placeholder=\"Current lesson topic\"></label>")
                    .append("<div style=\"display:flex; gap:20px;\">")
                    .append("<label style=\"flex:1;\">Start Time<input type=\"time\" name=\"startTime\" value=\"").append(LocalTime.now().toString().substring(0, 5)).append("\" required></label>")
                    .append("<label style=\"flex:1;\">End Time<input type=\"time\" name=\"endTime\" value=\"").append(LocalTime.now().plusHours(1).toString().substring(0, 5)).append("\" required></label>")
                    .append("</div>")
                    .append("<button type=\"submit\" style=\"margin-top:20px;\">Start Session & Show QR</button>")
                    .append("</form></div>");
        } else {
            // Show Active Session Info and QR
            content.append("<div style=\"display:flex; gap:30px; flex-wrap:wrap;\">")
                    .append("<div style=\"flex:1; min-width:300px; text-align:center;\">")
                    .append("<div class=\"status present\" style=\"display:inline-block; margin-bottom:10px;\">SESSION ACTIVE</div>")
                    .append("<h2>").append(AttendanceServer.escape(tokenService.getActiveSubject())).append("</h2>")
                    .append("<p>").append(AttendanceServer.escape(tokenService.getActiveClassName())).append(" | ").append(AttendanceServer.escape(tokenService.getActiveTopic())).append("</p>")
                    
                    .append("<div id=\"qr-container\" style=\"margin:20px auto; width:300px; height:300px; background:#fff; border-radius:12px; display:flex; align-items:center; justify-content:center; overflow:hidden; border:2px solid var(--dark); padding:10px;\">")
                    .append("<div id=\"qr-box\" style=\"width:100%; height:100%;\">Loading QR...</div>")
                    .append("</div>")
                    .append("<p class=\"muted\">Scanning QR Code... (Updates every 5s)</p>")
                    
                    .append("<form method=\"post\" action=\"/teacher-stop-session\" style=\"margin-top:20px;\">")
                    .append("<button type=\"submit\" style=\"background:#dc2626;\">Stop Session & Finalize</button>")
                    .append("</form>")
                    .append("</div>");

            // Attendance List
            List<AttendanceRecord> todayRecords = store.findAttendanceByDate(LocalDate.now(), tokenService.getActiveSubject());
            StringBuilder rows = new StringBuilder();
            int count = 0;
            for (AttendanceRecord r : todayRecords) {
                if (r.getStartTime().equals(tokenService.getActiveStartTime())) {
                    count++;
                    rows.append("<tr><td>").append(AttendanceServer.escape(r.getStudentId()))
                            .append("</td><td>").append(AttendanceServer.escape(r.getStudentName()))
                            .append("</td><td>").append(r.getMarkedAt().toLocalTime().toString().substring(0, 8))
                            .append("</td><td class=\"success\">Present</td></tr>");
                }
            }
            if (count == 0) rows.append("<tr><td colspan=\"4\" class=\"muted\">No students scanned yet.</td></tr>");

            content.append("<div style=\"flex:1.5; min-width:300px;\">")
                    .append("<div style=\"display:flex; justify-content:space-between; align-items:center;\">")
                    .append("<h3>Live Attendance (").append(count).append(")</h3>")
                    .append("<a href=\"/teacher-export\" class=\"button-link secondary\" style=\"font-size:14px; padding:8px 15px;\">Download CSV</a>")
                    .append("</div>")
                    .append("<table><thead><tr><th>ID</th><th>Name</th><th>Time</th><th>Status</th></tr></thead><tbody>").append(rows).append("</tbody></table>")
                    .append("</div></div>");

            content.append("<script>")
                    .append("function updateQR() {")
                    .append("  fetch('/teacher-qr').then(r => r.text()).then(svg => {")
                    .append("    if (!svg) return;")
                    .append("    document.getElementById('qr-box').innerHTML = svg;")
                    .append("  });")
                    .append("}")
                    .append("updateQR(); setInterval(updateQR, 5000);")
                    .append("setInterval(() => { if(window.innerWidth > 800) location.reload(); }, 30000);")
                    .append("</script>");
        }

        AttendanceServer.sendHtml(exchange, 200, AttendanceServer.page("Teacher Dashboard", content.toString()));
    }

    public void handleHistory(HttpExchange exchange) throws IOException {
        Optional<Teacher> teacher = currentTeacher(exchange);
        if (teacher.isEmpty()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("<div class=\"eyebrow\">Attendance Records</div>");
        content.append("<h1>Global Attendance History</h1>");
        content.append("<p><a class=\"button-link secondary\" href=\"/teacher-dashboard\">Back to Dashboard</a></p>");

        for (String subject : store.findAllSubjects()) {
            List<ClassSession> sessions = store.findClassSessions(subject);
            if (sessions.isEmpty()) continue;

            content.append("<div class=\"date-card\" style=\"margin-top:30px;\">");
            content.append("<h2 style=\"margin-top:0;\">").append(AttendanceServer.escape(subject)).append("</h2>");
            content.append("<table><thead><tr><th>Date</th><th>Class</th><th>Time</th><th>Topic</th><th>Count</th></tr></thead><tbody>");
            
            for (ClassSession s : sessions) {
                int count = store.findAttendanceByDate(s.getDate(), subject).stream()
                        .filter(r -> r.getStartTime().equals(s.getStartTime()))
                        .toList().size();
                
                content.append("<tr><td>").append(s.getDate())
                        .append("</td><td>").append(AttendanceServer.escape(s.getClassName()))
                        .append("</td><td>").append(s.getStartTime()).append("-").append(s.getEndTime())
                        .append("</td><td>").append(AttendanceServer.escape(s.getTopic()))
                        .append("</td><td style=\"font-weight:700; color:var(--primary);\">").append(count)
                        .append("</td></tr>");
            }
            content.append("</tbody></table></div>");
        }

        AttendanceServer.sendHtml(exchange, 200, AttendanceServer.page("Attendance History", content.toString()));
    }

    public void handleStartSession(HttpExchange exchange) throws IOException {
        if (currentTeacher(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            
            try {
                tokenService.startSession(
                    form.get("className"),
                    form.get("subject"),
                    LocalTime.parse(form.get("startTime")),
                    LocalTime.parse(form.get("endTime")),
                    form.get("topic")
                );
            } catch (Exception e) {
                AttendanceServer.sendHtml(exchange, 400, AttendanceServer.page("Error", "<h1>Invalid session details</h1><p>" + e.getMessage() + "</p><p><a href='/teacher-dashboard'>Back</a></p>"));
                return;
            }
            AttendanceServer.redirect(exchange, "/teacher-dashboard");
        }
    }

    public void handleStopSession(HttpExchange exchange) throws IOException {
        if (currentTeacher(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }
        
        // Record the session in the store before stopping
        if (tokenService.isSessionActive()) {
            store.recordClassSession(
                tokenService.getActiveSubject(),
                LocalDate.now(),
                tokenService.getActiveClassName(),
                tokenService.getActiveStartTime(),
                tokenService.getActiveEndTime(),
                tokenService.getActiveTopic()
            );
        }
        
        tokenService.stopSession();
        AttendanceServer.redirect(exchange, "/teacher-dashboard");
    }

    public void handleQr(HttpExchange exchange) throws IOException {
        if (!tokenService.isSessionActive()) {
            AttendanceServer.sendHtml(exchange, 404, "");
            return;
        }
        String svg = QrCodeGenerator.generateSvg(tokenService.getCurrentToken().getValue());
        exchange.getResponseHeaders().add("Content-Type", "image/svg+xml");
        byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void handleExport(HttpExchange exchange) throws IOException {
        Optional<Teacher> teacher = currentTeacher(exchange);
        if (teacher.isEmpty() || !tokenService.isSessionActive()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }

        List<AttendanceRecord> records = store.findAttendanceByDate(LocalDate.now(), tokenService.getActiveSubject());
        StringBuilder csv = new StringBuilder("Student ID,Student Name,Marked At\n");
        for (AttendanceRecord r : records) {
            if (r.getStartTime().equals(tokenService.getActiveStartTime())) {
                csv.append(r.getStudentId()).append(",")
                   .append(r.getStudentName()).append(",")
                   .append(r.getMarkedAt()).append("\n");
            }
        }

        exchange.getResponseHeaders().add("Content-Type", "text/csv");
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=attendance_" + tokenService.getActiveSubject() + ".csv");
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Optional<Teacher> currentTeacher(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId == null || !teacherSessions.containsKey(sessionId)) return Optional.empty();
        return store.findTeacherById(teacherSessions.get(sessionId));
    }

    private String currentSessionId(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String c : cookie.split(";")) {
            String[] parts = c.trim().split("=");
            if (parts.length == 2 && "TEACHER_SESSION".equals(parts[0])) return parts[1];
        }
        return null;
    }
}
