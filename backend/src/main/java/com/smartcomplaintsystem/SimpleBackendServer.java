package com.smartcomplaintsystem;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleBackendServer {
    private static final int PORT = 8080;
    private static final String TOKEN_SECRET = "SCS_DEMO_SECRET_CHANGE_ME";
    private static final long TOKEN_TTL_SECONDS = 60 * 60;
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "REJECTED");

    private static final Map<String, UserRecord> users = new HashMap<>();
    private static final List<Complaint> complaints = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong idSequence = new AtomicLong(1);

    public static void main(String[] args) throws IOException {
        seedUsers();
        seedComplaints();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());
        server.createContext("/api/complaints", new ComplaintsHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Backend running on http://localhost:" + PORT);
    }

    private static void seedUsers() {
        users.put("user", new UserRecord("user", "user123", "USER"));
        users.put("admin", new UserRecord("admin", "admin123", "ADMIN"));
    }

    private static void seedComplaints() {
        complaints.add(new Complaint(idSequence.getAndIncrement(), "user", "Street light not working", "Street light outside block A is off since two days.", "OPEN", Instant.now().minusSeconds(7200).toString(), "Infrastructure", "HIGH", "Block A - Main Gate", "Phone", "2026-03-20"));
        complaints.add(new Complaint(idSequence.getAndIncrement(), "user", "Water leakage", "Water is leaking near parking area.", "IN_PROGRESS", Instant.now().minusSeconds(5400).toString(), "Water", "MEDIUM", "Parking Area", "Email", "2026-03-21"));
        complaints.add(new Complaint(idSequence.getAndIncrement(), "admin", "Portal feedback", "Admin dashboard pagination is helpful.", "RESOLVED", Instant.now().minusSeconds(3600).toString(), "System", "LOW", "Online", "In App", "2026-03-22"));
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\",\"timestamp\":\"" + Instant.now() + "\"}");
        }
    }

    private static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptions(exchange);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readBody(exchange.getRequestBody());
            String username = extractJsonString(body, "username");
            String password = extractJsonString(body, "password");
            String role = extractJsonString(body, "role").toUpperCase(Locale.ROOT);

            if (isBlank(username) || isBlank(password) || isBlank(role)) {
                sendJson(exchange, 400, "{\"error\":\"username, password and role are required\"}");
                return;
            }

            UserRecord user = users.get(username);
            if (user == null || !user.password.equals(password)) {
                sendJson(exchange, 401, "{\"error\":\"Invalid credentials\"}");
                return;
            }

            if (!user.role.equals(role)) {
                sendJson(exchange, 403, "{\"error\":\"Role mismatch for selected login page\"}");
                return;
            }

            long expiresAt = Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS;
            String token = createToken(user.username, user.role, expiresAt);
            sendJson(exchange, 200,
                "{\"token\":\"" + token + "\",\"role\":\"" + user.role + "\",\"username\":\"" + user.username + "\",\"expiresAt\":" + expiresAt + "}");
        }
    }

    private static class ComplaintsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptions(exchange);
                return;
            }

            AuthContext auth = requireAuth(exchange);
            if (auth == null) {
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, auth);
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                if (!"USER".equals(auth.role)) {
                    sendJson(exchange, 403, "{\"error\":\"Only users can create complaints\"}");
                    return;
                }
                handlePost(exchange, auth);
                return;
            }

            if ("PATCH".equalsIgnoreCase(method)) {
                if (!"ADMIN".equals(auth.role)) {
                    sendJson(exchange, 403, "{\"error\":\"Only admins can update status\"}");
                    return;
                }
                handlePatch(exchange);
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }

        private void handleGet(HttpExchange exchange, AuthContext auth) throws IOException {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            synchronized (complaints) {
                for (Complaint complaint : complaints) {
                    if ("USER".equals(auth.role) && !auth.username.equals(complaint.userName)) {
                        continue;
                    }
                    if (!first) {
                        json.append(',');
                    }
                    json.append(complaint.toJson());
                    first = false;
                }
            }
            json.append(']');
            sendJson(exchange, 200, json.toString());
        }

        private void handlePost(HttpExchange exchange, AuthContext auth) throws IOException {
            String body = readBody(exchange.getRequestBody());
            String title = extractJsonString(body, "title");
            String description = extractJsonString(body, "description");
            String category = extractJsonString(body, "category");
            String priority = extractJsonString(body, "priority");
            String location = extractJsonString(body, "location");
            String preferredContact = extractJsonString(body, "preferredContact");
            String incidentDate = extractJsonString(body, "incidentDate");

            if (isBlank(title) || isBlank(description)) {
                sendJson(exchange, 400, "{\"error\":\"title and description are required\"}");
                return;
            }

            Complaint complaint = new Complaint(
                idSequence.getAndIncrement(),
                sanitize(auth.username),
                sanitize(title),
                sanitize(description),
                "OPEN",
                Instant.now().toString(),
                sanitize(isBlank(category) ? "General" : category),
                sanitize(isBlank(priority) ? "MEDIUM" : priority),
                sanitize(isBlank(location) ? "Not specified" : location),
                sanitize(isBlank(preferredContact) ? "In App" : preferredContact),
                sanitize(isBlank(incidentDate) ? "Not provided" : incidentDate)
            );
            complaints.add(complaint);
            sendJson(exchange, 201, complaint.toJson());
        }

        private void handlePatch(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            Matcher matcher = Pattern.compile("^/api/complaints/(\\d+)/status$").matcher(path);
            if (!matcher.find()) {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
                return;
            }

            long id = Long.parseLong(matcher.group(1));
            String body = readBody(exchange.getRequestBody());
            String status = extractJsonString(body, "status").toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(status)) {
                sendJson(exchange, 400, "{\"error\":\"Invalid status\"}");
                return;
            }

            synchronized (complaints) {
                for (Complaint complaint : complaints) {
                    if (complaint.id == id) {
                        complaint.status = status;
                        sendJson(exchange, 200, complaint.toJson());
                        return;
                    }
                }
            }

            sendJson(exchange, 404, "{\"error\":\"Complaint not found\"}");
        }
    }

    private static class AnalyticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptions(exchange);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            AuthContext auth = requireAuth(exchange);
            if (auth == null) {
                return;
            }

            if (!"ADMIN".equals(auth.role)) {
                sendJson(exchange, 403, "{\"error\":\"Only admins can access analytics\"}");
                return;
            }

            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("OPEN", 0);
            counts.put("IN_PROGRESS", 0);
            counts.put("RESOLVED", 0);
            counts.put("REJECTED", 0);

            synchronized (complaints) {
                for (Complaint complaint : complaints) {
                    counts.put(complaint.status, counts.getOrDefault(complaint.status, 0) + 1);
                }
            }

            String json = "{" +
                "\"total\":" + complaints.size() + "," +
                "\"OPEN\":" + counts.get("OPEN") + "," +
                "\"IN_PROGRESS\":" + counts.get("IN_PROGRESS") + "," +
                "\"RESOLVED\":" + counts.get("RESOLVED") + "," +
                "\"REJECTED\":" + counts.get("REJECTED") +
                "}";
            sendJson(exchange, 200, json);
        }
    }

    private static void sendOptions(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private static AuthContext requireAuth(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendJson(exchange, 401, "{\"error\":\"Missing bearer token\"}");
            return null;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        AuthContext auth = verifyToken(token);
        if (auth == null) {
            sendJson(exchange, 401, "{\"error\":\"Invalid or expired token\"}");
            return null;
        }
        return auth;
    }

    private static String createToken(String username, String role, long expiresAt) {
        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode("{\"sub\":\"" + sanitize(username) + "\",\"role\":\"" + sanitize(role) + "\",\"exp\":" + expiresAt + "}");
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    private static AuthContext verifyToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String username = extractJsonString(payload, "sub");
            String role = extractJsonString(payload, "role");
            long exp = extractJsonLong(payload, "exp");
            if (isBlank(username) || isBlank(role) || exp < Instant.now().getEpochSecond()) {
                return null;
            }
            return new AuthContext(username, role.toUpperCase(Locale.ROOT), exp);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] signature = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Unable to sign token", e);
        }
    }

    private static String base64UrlEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String extractJsonString(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static long extractJsonLong(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return 0;
        }
        return Long.parseLong(m.group(1));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String sanitize(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class Complaint {
        private final long id;
        private final String userName;
        private final String title;
        private final String description;
        private String status;
        private final String createdAt;
        private final String category;
        private final String priority;
        private final String location;
        private final String preferredContact;
        private final String incidentDate;

        private Complaint(long id, String userName, String title, String description, String status, String createdAt, String category, String priority, String location, String preferredContact, String incidentDate) {
            this.id = id;
            this.userName = userName;
            this.title = title;
            this.description = description;
            this.status = status;
            this.createdAt = createdAt;
            this.category = category;
            this.priority = priority;
            this.location = location;
            this.preferredContact = preferredContact;
            this.incidentDate = incidentDate;
        }

        private String toJson() {
            return "{" +
                "\"id\":" + id + "," +
                "\"userName\":\"" + userName + "\"," +
                "\"title\":\"" + title + "\"," +
                "\"description\":\"" + description + "\"," +
                "\"status\":\"" + status + "\"," +
                "\"createdAt\":\"" + createdAt + "\"," +
                "\"category\":\"" + category + "\"," +
                "\"priority\":\"" + priority + "\"," +
                "\"location\":\"" + location + "\"," +
                "\"preferredContact\":\"" + preferredContact + "\"," +
                "\"incidentDate\":\"" + incidentDate + "\"" +
                "}";
        }
    }

    private static class UserRecord {
        private final String username;
        private final String password;
        private final String role;

        private UserRecord(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    private static class AuthContext {
        private final String username;
        private final String role;
        @SuppressWarnings("unused")
        private final long exp;

        private AuthContext(String username, String role, long exp) {
            this.username = username;
            this.role = role;
            this.exp = exp;
        }
    }
}
