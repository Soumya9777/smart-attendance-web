package smartattendance.web;

import com.sun.net.httpserver.HttpExchange;
import smartattendance.model.Teacher;
import smartattendance.model.AttendanceRecord;
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

        String activeSub = tokenService.getActiveSubject();
        List<AttendanceRecord> todayRecords = store.findAttendanceByDate(LocalDate.now(), activeSub);
        
        StringBuilder attendanceRows = new StringBuilder();
        for (AttendanceRecord r : todayRecords) {
            if (r.getStartTime().equals(tokenService.getActiveStartTime())) {
                attendanceRows.append("<tr><td>").append(AttendanceServer.escape(r.getStudentId()))
                        .append("</td><td>").append(AttendanceServer.escape(r.getStudentName()))
                        .append("</td><td>").append(r.getMarkedAt().toLocalTime().toString().substring(0, 8))
                        .append("</td><td class=\"success\">Present</td></tr>");
            }
        }
        if (attendanceRows.length() == 0) {
            attendanceRows.append("<tr><td colspan=\"4\" class=\"muted\">No students have marked attendance for this session yet.</td></tr>");
        }

        StringBuilder subjectOptions = new StringBuilder();
        for (String sub : store.findAllSubjects()) {
            subjectOptions.append("<option value=\"").append(AttendanceServer.escape(sub))
                    .append("\"").append(sub.equals(activeSub) ? " selected" : "").append(">")
                    .append(AttendanceServer.escape(sub)).append("</option>");
        }

        String body = "<div class=\"eyebrow\">Teacher Dashboard</div>"
                + "<h1>Welcome, " + AttendanceServer.escape(teacher.getName()) + "</h1>"
                + "<p><a class=\"button-link secondary\" href=\"/teacher-logout\">Logout</a></p>"
                + "<div class=\"action-panel\">"
                + "<div><strong>Current Session: " + AttendanceServer.escape(activeSub) + "</strong>"
                + "<span>Class: " + AttendanceServer.escape(tokenService.getActiveClassName()) + " | "
                + tokenService.getActiveStartTime() + " to " + tokenService.getActiveEndTime() + "</span></div>"
                + "<a href=\"/teacher-export\" class=\"button-link\">Download CSV Report</a>"
                + "</div>"
                + "<div style=\"display:flex; gap:20px; flex-wrap:wrap;\">"
                + "<div style=\"flex:1; min-width:300px;\"><h2>Start New Session</h2>"
                + "<form method=\"post\" action=\"/teacher-start-session\">"
                + "<label>Class Name<input name=\"className\" required value=\"" + AttendanceServer.escape(tokenService.getActiveClassName()) + "\"></label>"
                + "<label>Subject<select name=\"subject\" required style=\"width:100%; padding:14px; border-radius:12px; border:1px solid #d1d5db; background:#f9fafb; font-size:16px;\">" + subjectOptions + "</select></label>"
                + "<label>Topic<input name=\"topic\" required value=\"" + AttendanceServer.escape(tokenService.getActiveTopic()) + "\"></label>"
                + "<div style=\"display:flex;gap:10px;\"><label style=\"flex:1\">Start Time<input type=\"time\" name=\"startTime\" required value=\"" + tokenService.getActiveStartTime() + "\"></label>"
                + "<label style=\"flex:1\">End Time<input type=\"time\" name=\"endTime\" required value=\"" + tokenService.getActiveEndTime() + "\"></label></div>"
                + "<button type=\"submit\">Start QR Session</button>"
                + "</form></div>"
                + "<div style=\"flex:1; min-width:300px;\"><h2>Live QR Code</h2>"
                + "<p class=\"muted\">Students scan this code. It refreshes every " + TokenService.REFRESH_SECONDS + " seconds.</p>"
                + "<iframe src=\"/teacher-qr\" style=\"width:100%; height:320px; border:1px solid var(--line); border-radius:12px; background:white;\"></iframe>"
                + "</div>"
                + "</div>"
                + "<h2>Attendance for Current Session</h2>"
                + "<table><thead><tr><th>Student ID</th><th>Name</th><th>Marked At</th><th>Status</th></tr></thead>"
                + "<tbody>" + attendanceRows + "</tbody></table>";

        AttendanceServer.sendHtml(exchange, 200, AttendanceServer.page("Teacher Dashboard", body));
    }

    public void handleStartSession(HttpExchange exchange) throws IOException {
        Optional<Teacher> optionalTeacher = currentTeacher(exchange);
        if (optionalTeacher.isEmpty()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = AttendanceServer.parseQuery(body);
            
            try {
                String className = form.get("className");
                String subject = form.get("subject");
                String topic = form.get("topic");
                LocalTime startTime = LocalTime.parse(form.get("startTime"));
                LocalTime endTime = LocalTime.parse(form.get("endTime"));
                
                tokenService.setActiveSession(className, subject, startTime, endTime, topic);
                AttendanceServer.redirect(exchange, "/teacher-dashboard");
            } catch (Exception e) {
                AttendanceServer.sendHtml(exchange, 400, AttendanceServer.page("Error",
                        "<h1>Invalid Input</h1><p>" + AttendanceServer.escape(e.getMessage()) + "</p><a class=\"button-link\" href=\"/teacher-dashboard\">Back</a>"));
            }
        }
    }

    public void handleQr(HttpExchange exchange) throws IOException {
        Optional<Teacher> optionalTeacher = currentTeacher(exchange);
        if (optionalTeacher.isEmpty()) {
            AttendanceServer.sendHtml(exchange, 401, "Unauthorized");
            return;
        }

        String baseUrl = exchange.getRequestHeaders().getFirst("Host");
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        if (proto == null) proto = "http";
        String fullUrl = proto + "://" + baseUrl + "/scan?subject=" + 
                java.net.URLEncoder.encode(tokenService.getActiveSubject(), StandardCharsets.UTF_8) + 
                "&token=" + tokenService.getCurrentToken().getValue();

        boolean[][] matrix = QrCodeGenerator.generateQr(fullUrl);
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta http-equiv=\"refresh\" content=\"").append(TokenService.REFRESH_SECONDS).append("\"></head>");
        html.append("<body style=\"display:flex; justify-content:center; align-items:center; margin:0; background:white;\">");
        
        int size = matrix.length;
        html.append("<div style=\"display:grid; grid-template-columns:repeat(").append(size).append(", 6px); grid-template-rows:repeat(").append(size).append(", 6px);\">");
        for (boolean[] row : matrix) {
            for (boolean cell : row) {
                html.append("<div style=\"background:").append(cell ? "#000" : "#fff").append(";\"></div>");
            }
        }
        html.append("</div></body></html>");

        AttendanceServer.sendHtml(exchange, 200, html.toString());
    }

    public void handleExport(HttpExchange exchange) throws IOException {
        if (currentTeacher(exchange).isEmpty()) {
            AttendanceServer.redirect(exchange, "/teacher-login");
            return;
        }

        String subject = tokenService.getActiveSubject();
        List<AttendanceRecord> records = store.findAttendanceByDate(LocalDate.now(), subject);
        
        StringBuilder csv = new StringBuilder();
        csv.append("Student ID,Student Name,Subject,Date,Start Time,End Time,Marked At\n");
        for (AttendanceRecord r : records) {
            if (r.getStartTime().equals(tokenService.getActiveStartTime())) {
                csv.append(r.getStudentId()).append(",")
                   .append(r.getStudentName()).append(",")
                   .append(r.getSubject()).append(",")
                   .append(r.getAttendanceDate()).append(",")
                   .append(r.getStartTime()).append(",")
                   .append(r.getEndTime()).append(",")
                   .append(r.getMarkedAt()).append("\n");
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=attendance_" + subject + "_" + LocalDate.now() + ".csv");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Optional<Teacher> currentTeacher(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId == null) return Optional.empty();
        String teacherId = teacherSessions.get(sessionId);
        return teacherId != null ? store.findTeacherById(teacherId) : Optional.empty();
    }

    private String currentSessionId(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "TEACHER_SESSION".equals(parts[0])) return parts[1];
        }
        return null;
    }
}
