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
        server.createContext("/teacher-stop-session", teacherHandler::handleStopSession);
        server.createContext("/teacher-qr", teacherHandler::handleQr);
        server.createContext("/teacher-export", teacherHandler::handleExport);
        server.createContext("/teacher-history", teacherHandler::handleHistory);
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
                        + "<p class=\"muted\">Please login to scan attendance.</p>"
                        + "<div style=\"display:flex; flex-direction:column; gap:16px; margin-top:24px;\">"
                        + "<a class=\"button-link\" style=\"text-align:center;\" href=\"/student-login\">Student Portal / Login</a>"
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
        StringBuilder subjectCards = new StringBuilder();
        List<String> studentSubjects = store.findSubjectsForStudent(student.getId());
        
        for (String subject : studentSubjects) {
            int total = store.countClassSessions(subject);
            int present = store.countStudentAttendance(student.getId(), subject);
            int absent = total - present;
            double percentage = total == 0 ? 0 : (present * 100.0 / total);
            int percentageWidth = (int) Math.round(Math.min(100, percentage));
            
            String subjectId = "sub_" + subject.replaceAll("[^a-zA-Z0-9]", "");
            
            subjectCards.append("<div class=\"subject-card\" onclick=\"toggleDetails('").append(subjectId).append("')\">")
                    .append("<div class=\"subject-header\">")
                    .append("<h3>").append(escape(subject)).append("</h3>")
                    .append("<span class=\"percent-badge\">").append(String.format("%.1f%%", percentage)).append("</span>")
                    .append("</div>")
                    .append("<div class=\"stats-grid\">")
                    .append("<div class=\"stat\"><b>").append(present).append("</b><span>Present</span></div>")
                    .append("<div class=\"stat\"><b>").append(absent).append("</b><span>Absent</span></div>")
                    .append("<div class=\"stat\"><b>").append(total).append("</b><span>Total</span></div>")
                    .append("</div>")
                    .append("<div class=\"bar\"><i style=\"width:").append(percentageWidth).append("%\"></i></div>")
                    .append("<div id=\"").append(subjectId).append("\" class=\"history-details\" style=\"display:none;\">")
                    .append("<hr>")
                    .append(buildDateDetails(student.getId(), subject))
                    .append("</div>")
                    .append("</div>");
        }

        if (studentSubjects.isEmpty()) {
            subjectCards.append("<div class=\"warning\">No attendance recorded yet. Scan a QR code to start!</div>");
        }

        String body = "<div class=\"header-flex\">"
                + "<div><div class=\"eyebrow\">Student Portal</div><h1>" + escape(student.getName()) + "</h1></div>"
                + "<a class=\"logout-icon\" href=\"/student-logout\" title=\"Logout\">Logout</a>"
                + "</div>"
                + "<div class=\"action-panel\">"
                + "<div><strong>Active Session</strong><span>" + escape(tokenService.getActiveSubject().isEmpty() ? "No active class" : tokenService.getActiveSubject()) + "</span></div>"
                + "<a class=\"button-link\" href=\"/student-scan\">Start Scanning</a>"
                + "</div>"
                + "<h2>My Attendance</h2>"
                + "<div class=\"subjects-grid\">" + subjectCards + "</div>"
                + "<script>function toggleDetails(id){const el=document.getElementById(id);el.style.display=el.style.display==='none'?'block':'none';event.stopPropagation();}</script>";
        
        sendHtml(exchange, 200, page("Student Dashboard", body));
    }

    private void handleStudentScan(HttpExchange exchange) throws IOException {
        Optional<Student> optionalStudent = currentStudent(exchange);
        if (optionalStudent.isEmpty()) {
            redirect(exchange, "/student-login");
            return;
        }

        String body = "<div class=\"eyebrow\">QR Scanner</div>"
                + "<h1>Mark Attendance</h1>"
                + "<div id=\"scanner-container\" style=\"position:relative; width:100%; aspect-ratio:1/1; background:#000; border-radius:24px; overflow:hidden; box-shadow:0 20px 40px rgba(0,0,0,0.2);\">"
                + "<video id=\"preview\" style=\"width:100%; height:100%; object-fit:cover;\" autoplay playsinline></video>"
                + "<div id=\"scan-line\" style=\"position:absolute; top:0; left:0; width:100%; height:4px; background:var(--primary); box-shadow:0 0 20px var(--primary); animation:scan 2s infinite;\"></div>"
                + "</div>"
                + "<canvas id=\"scanCanvas\" hidden></canvas>"
                + "<p id=\"scanStatus\" style=\"text-align:center; margin-top:20px; font-weight:600; color:var(--muted);\">Initializing camera...</p>"
                + "<a class=\"button-link secondary\" style=\"width:100%; text-align:center; margin-top:10px;\" href=\"/student-dashboard\">Cancel</a>"
                + "<style>@keyframes scan { 0% { top: 0; } 50% { top: 100%; } 100% { top: 0; } }</style>"
                + "<script src=\"https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.min.js\"></script>"
                + "<script>"
                + "const status=document.getElementById('scanStatus');const video=document.getElementById('preview');const canvas=document.getElementById('scanCanvas');const ctx=canvas.getContext('2d');"
                + "let scanning=true;"
                + "function go(url){if(!url||!url.includes('/scan?')){status.textContent='Not a valid attendance QR.';scanning=true;return;}location.href=url;}"
                + "function scan(){if(!scanning)return;try{"
                + "if(video.readyState>=2&&video.videoWidth>0){"
                + "canvas.width=video.videoWidth;canvas.height=video.videoHeight;"
                + "ctx.drawImage(video,0,0,canvas.width,canvas.height);"
                + "const imageData=ctx.getImageData(0,0,canvas.width,canvas.height);"
                + "const code=jsQR(imageData.data,imageData.width,imageData.height,{inversionAttempts:'dontInvert'});"
                + "if(code){scanning=false;status.textContent='QR Detected! Marking...';go(code.data);return;}}"
                + "}catch(e){console.error(e);}requestAnimationFrame(scan);}"
                + "navigator.mediaDevices.getUserMedia({video:{facingMode:'environment'}}).then(stream => {"
                + "video.srcObject=stream;video.play();status.textContent='Scanning...';scan();"
                + "}).catch(err => {status.textContent='Camera Error. Enable permissions.';});"
                + "</script>";
        sendHtml(exchange, 200, page("Scan QR", body));
    }

    private String buildDateDetails(String studentId, String subject) throws IOException {
        List<ClassSession> classSessions = store.findClassSessions(subject);
        List<AttendanceRecord> presentRecords = store.findStudentAttendanceRecords(studentId, subject);
        StringBuilder details = new StringBuilder();
        if (classSessions.isEmpty()) return "<p class=\"muted\">No logs.</p>";

        details.append("<div class=\"mini-date-list\">");
        for (int i = classSessions.size() - 1; i >= 0; i--) {
            ClassSession session = classSessions.get(i);
            boolean present = hasAttendanceForSession(presentRecords, session);
            details.append("<div class=\"log-item ").append(present ? "present" : "absent").append("\">")
                    .append("<div><b>").append(session.getDate()).append("</b><br>")
                    .append("<small style=\"opacity:0.8\">").append(session.getStartTime()).append(" - ").append(session.getEndTime()).append("</small></div>")
                    .append("<span>").append(present ? "✅ Present" : "❌ Absent").append("</span>")
                    .append("</div>");
        }
        details.append("</div>");
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

    public static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">"
                + "<title>" + escape(title) + "</title>"
                + "<style>"
                + "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');"
                + ":root{--primary:#4f46e5;--primary-hover:#4338ca;--dark:#111827;--muted:#6b7280;--line:#e5e7eb;--bg-gradient:linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);--green:#059669;--card-bg:rgba(255, 255, 255, 0.95)}"
                + "*{box-sizing:border-box}body{font-family:'Inter',sans-serif;margin:0;background:var(--bg-gradient);color:#1f2937;min-height:100vh;padding:12px;overflow-x:hidden}"
                + "main{width:100%;max-width:760px;margin:0 auto;background:var(--card-bg);backdrop-filter:blur(10px);border-radius:24px;box-shadow:0 25px 50px -12px rgba(0,0,0,0.15);overflow:hidden;animation:fade-in 0.6s cubic-bezier(0.16,1,0.3,1)}"
                + "@keyframes fade-in{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}"
                + ".brand{display:flex;align-items:center;gap:12px;background:linear-gradient(135deg, var(--dark), #1f2937);padding:20px 24px}.brand img{width:50px;height:auto}.brand strong{display:block;color:white;font-size:18px}.brand span{display:block;color:#9ca3af;font-size:12px}"
                + ".content{padding:24px 20px}.eyebrow{display:inline-block;margin-bottom:8px;color:var(--primary);font-size:12px;font-weight:800;letter-spacing:0.1em;text-transform:uppercase}"
                + "h1{margin:0 0 16px;font-size:24px;color:var(--dark);letter-spacing:-0.03em;font-weight:800}h2{font-size:18px;margin:24px 0 12px;color:var(--dark);font-weight:700}"
                + ".header-flex{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:20px}"
                + ".logout-icon{padding:8px 16px;background:#fee2e2;color:#dc2626;border-radius:12px;font-size:13px;font-weight:700;text-decoration:none}"
                + ".action-panel{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:20px 0;padding:16px;border:1px solid var(--line);border-radius:16px;background:white;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05)}"
                + ".action-panel strong{display:block;font-size:12px;color:var(--muted);text-transform:uppercase}.action-panel span{display:block;font-weight:700;color:var(--dark);font-size:15px}"
                + ".button-link{display:inline-block;background:var(--primary);color:white!important;padding:12px 20px;border-radius:12px;font-weight:600;text-decoration:none;transition:all 0.2s}.button-link:hover{transform:translateY(-1px);background:var(--primary-hover)}"
                + ".secondary{background:#f3f4f6!important;color:var(--dark)!important}"
                + ".subjects-grid{display:grid;grid-template-columns:1fr;gap:16px}"
                + ".subject-card{background:white;border:1px solid var(--line);border-radius:20px;padding:20px;cursor:pointer;transition:all 0.2s;box-shadow:0 2px 4px rgba(0,0,0,0.02)}.subject-card:hover{border-color:var(--primary);box-shadow:0 10px 15px -3px rgba(0,0,0,0.1)}"
                + ".subject-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.subject-header h3{margin:0;font-size:16px;color:var(--dark)}"
                + ".percent-badge{background:var(--primary);color:white;padding:4px 10px;border-radius:8px;font-weight:700;font-size:13px}"
                + ".stats-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:16px}"
                + ".stat{text-align:center}.stat b{display:block;font-size:18px;color:var(--dark)}.stat span{display:block;font-size:11px;color:var(--muted);text-transform:uppercase}"
                + ".bar{height:6px;background:#f3f4f6;border-radius:10px;overflow:hidden}.bar i{display:block;height:100%;background:var(--primary);border-radius:10px}"
                + ".history-details{margin-top:16px;animation:slide-down 0.3s ease-out}@keyframes slide-down{from{opacity:0;transform:translateY(-10px)}to{opacity:1;transform:translateY(0)}}"
                + ".mini-date-list{display:flex;flex-direction:column;gap:8px;max-height:200px;overflow-y:auto;padding-right:4px}"
                + ".log-item{display:flex;justify-content:space-between;padding:10px;border-radius:10px;font-size:13px}.present{background:#ecfdf5;color:#065f46}.absent{background:#fff1f2;color:#991b1b}"
                + "form label{display:block;margin-bottom:16px;font-weight:600;color:var(--muted);font-size:14px}"
                + "form input{width:100%;padding:14px;margin-top:8px;border:1px solid var(--line);border-radius:12px;font-size:16px;background:#f9fafb}"
                + "form button{width:100%;padding:16px;background:var(--primary);color:white;border:0;border-radius:12px;font-weight:700;font-size:16px;margin-top:12px}"
                + "</style></head><body><main><div class=\"brand\"><img src=\"/logo.png\" alt=\"NIST\"><div><strong>NIST Attendance</strong><span>Smart Campus Portal</span></div></div><div class=\"content\">"
                + body + "</div></main></body></html>";
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
