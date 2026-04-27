package smartattendance.web;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TokenService {
    // Increased to 60 seconds because free servers like Render can be slow
    public static final int REFRESH_SECONDS = 60;

    private final SecureRandom random = new SecureRandom();
    private final List<TokenListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executorService;
    private volatile AttendanceToken currentToken = null;
    private volatile AttendanceToken previousToken = null;
    
    private volatile boolean sessionActive = false;
    private volatile String activeSubject = "";
    private volatile String activeClassName = "";
    private volatile String activeTopic = "";
    private volatile int activeDurationMinutes = 0;
    private volatile LocalTime activeStartTime = null;
    private volatile LocalTime activeEndTime = null;

    public void start() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::rotateToken, 0, REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public AttendanceToken getCurrentToken() {
        return currentToken;
    }

    public boolean isValid(String tokenValue) {
        if (!sessionActive || tokenValue == null) return false;

        // Check current token
        AttendanceToken curr = currentToken;
        if (curr != null && curr.getValue().equals(tokenValue) && Instant.now().isBefore(curr.getExpiresAt())) {
            return true;
        }

        // Check previous token (allows a grace period if the QR just updated)
        AttendanceToken prev = previousToken;
        if (prev != null && prev.getValue().equals(tokenValue) && Instant.now().isBefore(prev.getExpiresAt().plusSeconds(REFRESH_SECONDS))) {
            return true;
        }

        return false;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    public String getActiveSubject() {
        return activeSubject;
    }

    public String getActiveClassName() {
        return activeClassName;
    }

    public String getActiveTopic() {
        return activeTopic;
    }

    public int getActiveDurationMinutes() {
        return activeDurationMinutes;
    }

    public LocalTime getActiveStartTime() {
        return activeStartTime;
    }

    public LocalTime getActiveEndTime() {
        return activeEndTime;
    }

    public void stopSession() {
        this.sessionActive = false;
        this.currentToken = null;
        this.previousToken = null;
    }

    public void startSession(String activeClassName, String activeSubject, LocalTime activeStartTime,
                                  LocalTime activeEndTime, String activeTopic) {
        if (activeClassName == null || activeClassName.isBlank()) {
            throw new IllegalArgumentException("Class cannot be empty");
        }
        if (activeSubject == null || activeSubject.isBlank()) {
            throw new IllegalArgumentException("Subject cannot be empty");
        }
        int activeDurationMinutes = calculateDuration(activeStartTime, activeEndTime);
        if (activeDurationMinutes <= 0) {
            throw new IllegalArgumentException("Ending time must be after starting time");
        }
        this.activeClassName = activeClassName.trim();
        this.activeSubject = activeSubject.trim();
        this.activeStartTime = activeStartTime;
        this.activeEndTime = activeEndTime;
        this.activeTopic = activeTopic == null || activeTopic.isBlank() ? "Not specified" : activeTopic.trim();
        this.activeDurationMinutes = activeDurationMinutes;
        this.sessionActive = true;
        rotateToken();
    }

    private static int calculateDuration(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Starting and ending time are required");
        }
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    public void addListener(TokenListener listener) {
        listeners.add(listener);
        if (currentToken != null) {
            listener.onTokenChanged(currentToken);
        }
    }

    private void rotateToken() {
        if (!sessionActive) {
            currentToken = null;
            previousToken = null;
            return;
        }
        previousToken = currentToken;
        currentToken = newToken();
        for (TokenListener listener : listeners) {
            listener.onTokenChanged(currentToken);
        }
    }

    private AttendanceToken newToken() {
        byte[] bytes = new byte[9];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new AttendanceToken(value, Instant.now().plusSeconds(REFRESH_SECONDS));
    }

    public interface TokenListener {
        void onTokenChanged(AttendanceToken token);
    }

    public static class AttendanceToken {
        private final String value;
        private final Instant expiresAt;

        public AttendanceToken(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        public String getValue() {
            return value;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
