package smartattendance.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import smartattendance.model.AttendanceRecord;
import smartattendance.model.ClassSession;
import smartattendance.model.Student;
import smartattendance.store.AttendanceStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public class AttendanceServer {
    private final int port;
    private final AttendanceStore store;
    private final TokenService tokenService;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> studentSessions = new ConcurrentHashMap<>();
    private HttpServer server;
    private String baseUrl;

    public AttendanceServer(int port, AttendanceStore store, TokenService tokenService) {
        this.port = port;
        this.store = store;
        this.tokenService = tokenService;
    }

    public void start() throws IOException {
        String localAddress = findLocalAddress();
        boolean secure = Files.exists(Path.of("data", "attendance-keystore.p12"));
        server = secure ? createHttpsServer() : HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleHome);
        server.createContext("/logo.png", this::handleLogo);
        server.createContext("/student-login", this::handleStudentLogin);
        server.createContext("/student-logout", this::handleStudentLogout);
        server.createContext("/student-dashboard", this::handleStudentDashboard);
        server.createContext("/student-scan", this::handleStudentScan);
        server.createContext("/scan", this::handleScan);

        TeacherWebHandler teacherHandler = new TeacherWebHandler(store, tokenService);
        AdminWebHandler adminHandler = new AdminWebHandler(store);

        server.createContext("/admin-login", adminHandler::handleLogin);
        server.createContext("/admin-logout", adminHandler::handleLogout);
        server.createContext("/admin-dashboard", adminHandler::handleDashboard);
        server.createContext("/admin-add-teacher", adminHandler::handleAddTeacher);
        server.createContext("/admin-add-student", adminHandler::handleAddStudent);
        server.createContext("/admin-add-subject", adminHandler::handleAddSubject);
        server.createContext("/admin-delete-user", adminHandler::handleDeleteUser);
        
        server.createContext("/teacher-login", teacherHandler::handleLogin);
        server.createContext("/teacher-logout", teacherHandler::handleLogout);
        server.createContext("/teacher-dashboard", teacherHandler::handleDashboard);
        server.createContext("/teacher-start-session", teacherHandler::handleStartSession);
        server.createContext("/teacher-qr", teacherHandler::handleQr);
        server.createContext("/teacher-export", teacherHandler::handleExport);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        baseUrl = (secure ? "https://" : "http://") + localAddress + ":" + port;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private HttpsServer createHttpsServer() throws IOException {
        try {
            char[] password = "attendance123".toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (java.io.InputStream inputStream = Files.newInputStream(Path.of("data", "attendance-keystore.p12"))) {
                keyStore.load(inputStream, password);
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

            HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            return httpsServer;
        } catch (Exception exception) {
            throw new IOException("Could not start HTTPS server. Run scripts/setup-https.sh again.", exception);
        }
    }

    private void handleHome(HttpExchange exchange) throws IOException {
        sendHtml(exchange, 200, page("Smart Attendance Portal",
                "<div class=\"eyebrow\">Welcome</div>"
                        + "<h1>Smart Attendance System</h1>"
                        + "<p class=\"muted\">Select your portal to continue.</p>"
                        + "<div style=\"display:flex; flex-direction:column; gap:16px; margin-top:24px;\">"
                        + "<a class=\"button-link\" style=\"text-align:center;\" href=\"/student-login\">Student Portal</a>"
                        + "<a class=\"button-link secondary\" style=\"text-align:center;\" href=\"/teacher-login\">Teacher Portal</a>"
                        + "<a class=\"button-link secondary\" style=\"text-align:center;\" href=\"/admin-login\">Admin Portal</a>"
                        + "</div>"));
    }

    private void handleLogo(HttpExchange exchange) throws IOException {
        Path logoPath = Path.of("nist.png");
        if (Files.notExists(logoPath)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = Files.readAllBytes(logoPath);
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void handleStudentLogin(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String form = "<div class=\"eyebrow\">Student Access</div>"
                    + "<h1>Login to continue</h1>"
                    + "<form method=\"post\" action=\"/student-login\">"
                    + "<label>Student ID<input name=\"studentId\" required autofocus></label>"
                    + "<label>Password<input name=\"password\" type=\"password\" required></label>"
                    + "<button type=\"submit\">Login</button>"
                    + "</form>";
            sendHtml(exchange, 200, page("Student Login", form));
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(exchange, 405, page("Method Not Allowed", "<h1>Use the login form</h1>"));
            return;
        }

        Map<String, String> form = readForm(exchange);
        Optional<Student> optionalStudent = store.findStudentById(form.getOrDefault("studentId", ""));
        if (optionalStudent.isEmpty() || !optionalStudent.get().passwordMatches(form.getOrDefault("password", ""))) {
            sendHtml(exchange, 401, page("Login Failed",
                    "<h1>Invalid student ID or password</h1><p>Please try your assigned login.</p>"));
            return;
        }

        String sessionId = newSessionId();
        studentSessions.put(sessionId, optionalStudent.get().getId());
        exchange.getResponseHeaders().add("Set-Cookie", "STUDENT_SESSION=" + sessionId + "; Path=/; HttpOnly");
        redirect(exchange, "/student-dashboard");
    }

    private void handleStudentLogout(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId != null) {
            studentSessions.remove(sessionId);
        }
        exchange.getResponseHeaders().add("Set-Cookie",
                "STUDENT_SESSION=; Path=/; Max-Age=0; HttpOnly");
        sendHtml(exchange, 200, page("Logged Out",
                "<div class=\"eyebrow\">Logged Out</div>"
                        + "<h1>You are logged out</h1>"
                        + "<p class=\"muted\">Your student session has ended.</p>"
                        + "<p><a class=\"button-link\" href=\"/student-login\">Login Again</a></p>"));
    }

    private void handleStudentDashboard(HttpExchange exchange) throws IOException {
        Optional<Student> optionalStudent = currentStudent(exchange);
        if (optionalStudent.isEmpty()) {
            redirect(exchange, "/student-login");
            return;
        }

        Student student = optionalStudent.get();
        StringBuilder rows = new StringBuilder();
        StringBuilder dateDetails = new StringBuilder();
        List<String> studentSubjects = store.findSubjectsForStudent(student.getId());
        for (String subject : studentSubjects) {
            int total = store.countClassSessions(subject);
            int present = store.countStudentAttendance(student.getId(), subject);
            double percentage = total == 0 ? 0 : (present * 100.0 / total);
            int percentageWidth = (int) Math.round(Math.min(100, percentage));
            rows.append("<tr><td><strong>").append(escape(subject)).append("</strong></td><td>").append(present)
                    .append("</td><td>").append(total).append("</td><td>")
                    .append("<div class=\"percent\"><span>").append(String.format("%.2f%%", percentage))
                    .append("</span><div class=\"bar\"><i style=\"width:")
                    .append(percentageWidth).append("%\"></i></div></div></td></tr>");
            dateDetails.append(buildDateDetails(student.getId(), subject));
        }
        if (studentSubjects.isEmpty()) {
            rows.append("<tr><td colspan=\"4\" class=\"muted\">No subjects yet. Scan your first class QR to add a subject.</td></tr>");
            dateDetails.append("<section class=\"date-card\"><p class=\"muted\">No date-wise records yet.</p></section>");
        }

        String body = "<div class=\"eyebrow\">Student Dashboard</div>"
                + "<h1>Welcome, " + escape(student.getName()) + "</h1>"
                + "<p><a class=\"button-link secondary\" href=\"/student-logout\">Logout</a></p>"
                + "<p class=\"muted\">You are logged in. Now use the scan option when the teacher starts the QR session.</p>"
                + "<div class=\"action-panel\"><div><strong>Active Subject</strong><span>"
                + escape(tokenService.getActiveSubject()) + "</span></div>"
                + "<a class=\"button-link\" href=\"/student-scan\">Open QR Scanner</a></div>"
                + "<h2>Subject Attendance</h2>"
                + "<table><thead><tr><th>Subject</th><th>Present</th><th>Total</th><th>Percentage</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table>"
                + "<h2>Date-wise Status</h2>"
                + dateDetails;
        sendHtml(exchange, 200, page("Student Dashboard", body));
    }

    private void handleStudentScan(HttpExchange exchange) throws IOException {
        Optional<Student> optionalStudent = currentStudent(exchange);
        if (optionalStudent.isEmpty()) {
            redirect(exchange, "/student-login");
            return;
        }

        String body = "<div class=\"eyebrow\">QR Scanner</div>"
                + "<h1>Scan after login</h1>"
                + "<p class=\"muted\">Point your phone camera at the QR code shown on the teacher dashboard.</p>"
                + "<p id=\"secureWarning\" class=\"warning\" hidden>This page is not running on HTTPS, so your mobile browser will block live camera access. Ask the teacher to run HTTPS setup.</p>"
                + "<video id=\"preview\" autoplay playsinline></video>"
                + "<canvas id=\"scanCanvas\" hidden></canvas>"
                + "<p id=\"scanStatus\" class=\"muted\">Starting camera...</p>"
                + "<p><a class=\"button-link secondary\" href=\"/student-dashboard\">Back to Dashboard</a></p>"
                + "<script>"
                + "const status=document.getElementById('scanStatus');const video=document.getElementById('preview');const canvas=document.getElementById('scanCanvas');const ctx=canvas.getContext('2d');"
                + "let detector=null;let scanning=false;"
                + "if(!window.isSecureContext){document.getElementById('secureWarning').hidden=false;}"
                + "function canDetect(){return 'BarcodeDetector' in window;}"
                + "function go(url){if(!url||!url.includes('/scan?')){status.textContent='QR found, but it is not an attendance QR. Scan the teacher QR.';return;}location.href=url;}"
                + "async function scanFrame(stream){if(!scanning){return;}try{"
                + "if(video.readyState>=2&&video.videoWidth>0){canvas.width=video.videoWidth;canvas.height=video.videoHeight;ctx.drawImage(video,0,0,canvas.width,canvas.height);"
                + "const codes=await detector.detect(canvas);if(codes.length>0){scanning=false;stream.getTracks().forEach(t=>t.stop());status.textContent='QR detected. Marking attendance...';go(codes[0].rawValue);return;}}"
                + "}catch(e){console.log(e);}setTimeout(()=>scanFrame(stream),180);}"
                + "async function start(){"
                + "if(!canDetect()){status.textContent='Live QR scanner is not supported in this browser. Please use Chrome on Android for automatic scanning.';return;}"
                + "if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){status.textContent='Live camera is blocked. Open this page with HTTPS in Chrome.';return;}"
                + "detector=detector||new BarcodeDetector({formats:['qr_code']});"
                + "const stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720}}});video.srcObject=stream;"
                + "await video.play();status.textContent='Camera ready. Hold the teacher QR steady inside the camera view.';scanning=true;scanFrame(stream);}"
                + "start().catch(()=>status.textContent='Live camera could not start. Check camera permission and HTTPS.');"
                + "</script>";
        sendHtml(exchange, 200, page("Scan QR", body));
    }

    private String buildDateDetails(String studentId, String subject) throws IOException {
        List<ClassSession> classSessions = store.findClassSessions(subject);
        List<AttendanceRecord> presentRecords = store.findStudentAttendanceRecords(studentId, subject);
        StringBuilder details = new StringBuilder();
        details.append("<section class=\"date-card\"><h3>").append(escape(subject)).append("</h3>");
        if (classSessions.isEmpty()) {
            details.append("<p class=\"muted\">No class sessions recorded yet.</p></section>");
            return details.toString();
        }

        details.append("<div class=\"date-grid\">");
        for (ClassSession session : classSessions) {
            boolean present = hasAttendanceForSession(presentRecords, session);
            details.append("<span class=\"status ")
                    .append(present ? "present" : "absent")
                    .append("\"><b>").append(session.getDate())
                    .append("</b><span>").append(session.getStartTime()).append("-").append(session.getEndTime())
                    .append("</span><span>").append(escape(session.getTopic()))
                    .append("</span><em>").append(present ? "Present" : "Absent")
                    .append("</em></span>");
        }
        details.append("</div></section>");
        return details.toString();
    }

    private static boolean hasAttendanceForSession(List<AttendanceRecord> records, ClassSession session) {
        for (AttendanceRecord record : records) {
            if (record.getAttendanceDate().equals(session.getDate())
                    && record.getStartTime().equals(session.getStartTime())
                    && record.getEndTime().equals(session.getEndTime())) {
                return true;
            }
        }
        return false;
    }

    private void handleScan(HttpExchange exchange) throws IOException {
        Optional<Student> optionalStudent = currentStudent(exchange);
        if (optionalStudent.isEmpty()) {
            redirect(exchange, "/student-login");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String token = query.getOrDefault("token", "");
        String subject = query.getOrDefault("subject", "");
        markAttendance(exchange, optionalStudent.get(), token, subject);
    }

    private void markAttendance(HttpExchange exchange, Student student, String token, String subject) throws IOException {
        if (!tokenService.isValid(token)) {
            sendHtml(exchange, 403, page("QR Expired",
                    "<h1>QR code expired</h1><p>Please scan the latest QR code from the teacher screen.</p>"));
            return;
        }
        if (!subject.equalsIgnoreCase(tokenService.getActiveSubject())) {
            sendHtml(exchange, 403, page("Wrong Subject",
                    "<h1>Subject changed</h1><p>Please scan the latest QR shown by the teacher.</p>"));
            return;
        }

        LocalTime startTime = tokenService.getActiveStartTime();
        LocalTime endTime = tokenService.getActiveEndTime();
        store.recordClassSession(subject, LocalDate.now(), tokenService.getActiveClassName(), startTime, endTime,
                tokenService.getActiveTopic());
        boolean marked = store.markAttendance(student, subject, LocalDate.now(), startTime, endTime);
        if (marked) {
            sendHtml(exchange, 200, page("Attendance Marked",
                    "<div class=\"eyebrow success\">Success</div><h1>Attendance marked</h1><p class=\"muted\">" + escape(student.getName())
                            + ", your attendance for " + escape(subject) + " "
                            + startTime + "-" + endTime + " is present.</p>"
                            + "<p><a class=\"button-link\" href=\"/student-dashboard\">View Attendance</a> "
                            + "<a class=\"button-link secondary\" href=\"/student-logout\">Logout</a></p>"));
        } else {
            sendHtml(exchange, 200, page("Already Marked",
                    "<div class=\"eyebrow\">Already Done</div><h1>Already marked for this class</h1><p class=\"muted\">Your attendance for "
                            + escape(subject) + " " + startTime + "-" + endTime + " is already present.</p>"
                            + "<p><a class=\"button-link\" href=\"/student-dashboard\">View Attendance</a> "
                            + "<a class=\"button-link secondary\" href=\"/student-logout\">Logout</a></p>"));
        }
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQuery(requestBody);
    }

    private Optional<Student> currentStudent(HttpExchange exchange) throws IOException {
        String sessionId = currentSessionId(exchange);
        if (sessionId == null) {
            return Optional.empty();
        }
        String studentId = studentSessions.get(sessionId);
        if (studentId != null) {
            return store.findStudentById(studentId);
        }
        return Optional.empty();
    }

    private String currentSessionId(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "STUDENT_SESSION".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    public static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int equalsAt = pair.indexOf('=');
            String key = equalsAt >= 0 ? pair.substring(0, equalsAt) : pair;
            String value = equalsAt >= 0 ? pair.substring(equalsAt + 1) : "";
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String newSessionId() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String findLocalAddress() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address.isSiteLocalAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        return InetAddress.getLocalHost().getHostAddress();
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    public static void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    public static String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + "</title>"
                + "<style>"
                + "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');"
                + ":root{--primary:#4f46e5;--primary-hover:#4338ca;--dark:#111827;--muted:#6b7280;--line:#e5e7eb;--bg-gradient:linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);--green:#059669;--card-bg:rgba(255, 255, 255, 0.95)}"
                + "*{box-sizing:border-box}body{font-family:'Inter',sans-serif;margin:0;background:var(--bg-gradient);color:#1f2937;display:grid;place-items:center;min-height:100vh;padding:24px}"
                + "main{width:min(760px,100%);background:var(--card-bg);backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);padding:0;border:1px solid rgba(255,255,255,0.4);border-radius:24px;box-shadow:0 25px 50px -12px rgba(0,0,0,0.15);overflow:hidden;animation:fade-in 0.6s cubic-bezier(0.16,1,0.3,1)}"
                + "@keyframes fade-in{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}"
                + ".brand{display:flex;align-items:center;gap:20px;background:linear-gradient(135deg, var(--dark), #1f2937);padding:24px 32px}.brand img{width:86px;height:auto;filter:drop-shadow(0 4px 6px rgba(0,0,0,0.3))}.brand strong{display:block;color:white;font-size:24px;letter-spacing:-0.02em}.brand span{display:block;color:#9ca3af;margin-top:6px;font-size:14px}"
                + ".content{padding:36px 32px}.eyebrow{display:inline-block;margin-bottom:12px;color:var(--primary);font-size:13px;font-weight:800;letter-spacing:0.1em;text-transform:uppercase}"
                + ".success{color:var(--green)}h1{margin:0 0 16px;font-size:32px;color:var(--dark);letter-spacing:-0.03em;font-weight:800}h2{font-size:20px;margin:32px 0 16px;color:var(--dark);font-weight:700;letter-spacing:-0.01em}"
                + "p{line-height:1.6;font-size:16px}.muted{color:var(--muted)}a{color:var(--primary);font-weight:600;text-decoration:none;transition:color 0.2s}a:hover{color:var(--primary-hover)}"
                + ".button-link{display:inline-block;background:var(--primary);color:white!important;padding:14px 24px;border-radius:12px;box-shadow:0 4px 6px -1px rgba(79,70,229,0.3);transition:all 0.2s cubic-bezier(0.4,0,0.2,1)}.button-link:hover{transform:translateY(-2px);box-shadow:0 10px 15px -3px rgba(79,70,229,0.4);background:var(--primary-hover)}"
                + ".action-panel{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:28px 0;padding:20px 24px;border:1px solid var(--line);border-radius:16px;background:rgba(249,250,251,0.8);box-shadow:inset 0 2px 4px rgba(0,0,0,0.02)}.action-panel span{display:block;color:var(--muted);margin-top:6px;font-size:15px}"
                + ".secondary{background:#f3f4f6!important;color:var(--dark)!important;box-shadow:none!important}.secondary:hover{background:#e5e7eb!important;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1)!important}video{width:100%;max-height:400px;background:#000;border-radius:16px;margin:16px 0;box-shadow:0 10px 25px -5px rgba(0,0,0,0.3)}"
                + ".warning{padding:16px 20px;border-radius:12px;background:#fff7ed;color:#9a3412;border:1px solid #fed7aa;display:flex;align-items:center;gap:12px;font-weight:500}"
                + "table{width:100%;border-collapse:separate;border-spacing:0;margin-top:16px;border:1px solid var(--line);border-radius:12px;overflow:hidden;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05)}"
                + "th,td{text-align:left;border-bottom:1px solid var(--line);padding:16px;font-size:15px}th{background:#f9fafb;color:#374151;font-weight:600;text-transform:uppercase;font-size:12px;letter-spacing:0.05em}tr:last-child td{border-bottom:0}tbody tr{transition:background 0.2s}tbody tr:hover{background:#f9fafb}"
                + ".date-card{border:1px solid var(--line);border-radius:16px;padding:20px;margin:16px 0;background:#ffffff;box-shadow:0 2px 4px rgba(0,0,0,0.02)}.date-card h3{margin:0 0 16px;color:var(--dark);font-size:18px}.date-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px}.status{border-radius:12px;padding:14px;border:1px solid var(--line);transition:transform 0.2s}.status:hover{transform:translateY(-2px)}.status b,.status span,.status em{display:block}.status span{color:var(--muted);font-size:13px;margin-top:4px}.status em{font-style:normal;margin-top:6px;font-weight:700}.present{background:#ecfdf5;border-color:#a7f3d0}.present em{color:var(--green)}.absent{background:#fef2f2;border-color:#fecaca}.absent em{color:#dc2626}"
                + ".percent{display:flex;align-items:center;gap:12px}.percent span{font-weight:700;min-width:55px}.bar{height:8px;background:#e5e7eb;border-radius:999px;flex-grow:1;overflow:hidden}.bar i{display:block;height:100%;background:linear-gradient(90deg, #34d399, #059669);border-radius:999px;transition:width 1s cubic-bezier(0.4,0,0.2,1)}"
                + "label{display:block;margin:20px 0;font-weight:600;color:#374151;font-size:15px}"
                + "input{width:100%;padding:14px 16px;margin-top:8px;border:1px solid #d1d5db;border-radius:12px;font-size:16px;transition:all 0.2s;background:#f9fafb}input:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 4px rgba(79,70,229,0.1);background:#fff}"
                + "button{width:100%;padding:16px;border:0;border-radius:12px;background:var(--primary);color:white;font-weight:700;font-size:16px;cursor:pointer;box-shadow:0 4px 6px -1px rgba(79,70,229,0.3);transition:all 0.2s}button:hover{background:var(--primary-hover);transform:translateY(-2px);box-shadow:0 10px 15px -3px rgba(79,70,229,0.4)}"
                + "</style></head><body><main><div class=\"brand\"><img src=\"/logo.png\" alt=\"NIST logo\"><div><strong>NIST Attendance Portal</strong><span>Smart Attendance Management System</span></div></div><div class=\"content\">"
                + body + "</div></main></body></html>";
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
