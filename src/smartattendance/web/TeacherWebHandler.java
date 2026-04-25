package smartattendance.web;

import com.sun.net.httpserver.HttpExchange;
import smartattendance.model.Teacher;
import smartattendance.store.AttendanceStore;
import smartattendance.qr.QrCodeGenerator;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

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

        String body = "<div class=\"eyebrow\">Teacher Dashboard</div>"
                + "<h1>Welcome, " + AttendanceServer.escape(teacher.getName()) + "</h1>"
                + "<p><a class=\"button-link secondary\" href=\"/teacher-logout\">Logout</a></p>"
                + "<div class=\"action-panel\">"
                + "<div><strong>Current Session: " + AttendanceServer.escape(tokenService.getActiveSubject()) + "</strong>"
                + "<span>Class: " + AttendanceServer.escape(tokenService.getActiveClassName()) + " | "
                + tokenService.getActiveStartTime() + " to " + tokenService.getActiveEndTime() + "</span></div>"
                + "</div>"
                + "<div style=\"display:flex; gap:20px; flex-wrap:wrap;\">"
                + "<div style=\"flex:1; min-width:300px;\"><h2>Start New Session</h2>"
                + "<form method=\"post\" action=\"/teacher-start-session\">"
                + "<label>Class Name<input name=\"className\" required placeholder=\"e.g., Year 2 CS\"></label>"
                + "<label>Subject<input name=\"subject\" required placeholder=\"e.g., Core Java\"></label>"
                + "<label>Topic<input name=\"topic\" required placeholder=\"e.g., Polymorphism\"></label>"
                + "<div style=\"display:flex;gap:10px;\"><label style=\"flex:1\">Start Time<input type=\"time\" name=\"startTime\" required value=\"09:00\"></label>"
                + "<label style=\"flex:1\">End Time<input type=\"time\" name=\"endTime\" required value=\"10:00\"></label></div>"
                + "<button type=\"submit\">Start QR Session</button>"
                + "</form></div>"
                + "<div style=\"flex:1; min-width:300px;\"><h2>Live QR Code</h2>"
                + "<p class=\"muted\">Students scan this code. It refreshes every " + TokenService.REFRESH_SECONDS + " seconds.</p>"
                + "<iframe src=\"/teacher-qr\" style=\"width:100%; height:320px; border:1px solid var(--line); border-radius:12px; background:white;\"></iframe>"
                + "</div></div>";

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
