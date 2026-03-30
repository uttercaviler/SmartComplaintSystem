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
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SimpleBackendServer {
    private static final int PORT = parsePort(System.getenv("PORT"), 8080);
    private static final String TOKEN_SECRET = envOrDefault("TOKEN_SECRET", "SCS_DEMO_SECRET_CHANGE_ME");
    private static final long TOKEN_TTL_SECONDS = 60 * 60;
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "REJECTED");
    private static final String DB_URL = envOrDefault(
        "DB_URL",
        "jdbc:mysql://localhost:3306/smart_complaint_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    );
    private static final String DB_USER = envOrDefault("DB_USER", "root");
    private static final String DB_PASSWORD = envOrDefault("DB_PASSWORD", "root");

    private static final Map<String, UserRecord> users = new HashMap<>();

    public static void main(String[] args) throws IOException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            initializeDatabase();
            seedUsers();
            loadUsers();
            seedComplaints();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MySQL backend", e);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());
        server.createContext("/api/complaints", new ComplaintsHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Backend running on http://localhost:" + PORT + " using MySQL");
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                    "username VARCHAR(64) PRIMARY KEY," +
                    "password VARCHAR(255) NOT NULL," +
                    "role VARCHAR(16) NOT NULL" +
                ")"
            );

            statement.execute(
                "CREATE TABLE IF NOT EXISTS complaints (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "user_name VARCHAR(64) NOT NULL," +
                    "title VARCHAR(255) NOT NULL," +
                    "description TEXT NOT NULL," +
                    "status VARCHAR(32) NOT NULL," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "category VARCHAR(64) NOT NULL," +
                    "priority VARCHAR(32) NOT NULL," +
                    "location VARCHAR(255) NOT NULL," +
                    "preferred_contact VARCHAR(64) NOT NULL," +
                    "incident_date VARCHAR(32) NOT NULL" +
                ")"
            );
        }
    }

    private static void seedUsers() throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement countStmt = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = countStmt.executeQuery()) {
            if (rs.next() && rs.getLong(1) > 0) {
                return;
            }
        }

        try (Connection connection = getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, ?)")) {
            insert.setString(1, "user");
            insert.setString(2, "user123");
            insert.setString(3, "USER");
            insert.executeUpdate();

            insert.setString(1, "admin");
            insert.setString(2, "admin123");
            insert.setString(3, "ADMIN");
            insert.executeUpdate();
        }
    }

    private static void loadUsers() throws SQLException {
        users.clear();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT username, password, role FROM users");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                String password = rs.getString("password");
                String role = rs.getString("role");
                users.put(username, new UserRecord(username, password, role));
            }
        }
    }

    private static void seedComplaints() throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement countStmt = connection.prepareStatement("SELECT COUNT(*) FROM complaints");
             ResultSet rs = countStmt.executeQuery()) {
            if (rs.next() && rs.getLong(1) > 0) {
                return;
            }
        }

        String sql = "INSERT INTO complaints (user_name, title, description, status, created_at, category, priority, location, preferred_contact, incident_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement insert = connection.prepareStatement(sql)) {
            insertComplaintSeed(insert, "user", "Street light not working", "Street light outside block A is off since two days.", "OPEN", Instant.now().minusSeconds(7200), "Infrastructure", "HIGH", "Block A - Main Gate", "Phone", "2026-03-20");
            insertComplaintSeed(insert, "user", "Water leakage", "Water is leaking near parking area.", "IN_PROGRESS", Instant.now().minusSeconds(5400), "Water", "MEDIUM", "Parking Area", "Email", "2026-03-21");
            insertComplaintSeed(insert, "admin", "Portal feedback", "Admin dashboard pagination is helpful.", "RESOLVED", Instant.now().minusSeconds(3600), "System", "LOW", "Online", "In App", "2026-03-22");
        }
    }

    private static void insertComplaintSeed(
        PreparedStatement insert,
        String userName,
        String title,
        String description,
        String status,
        Instant createdAt,
        String category,
        String priority,
        String location,
        String preferredContact,
        String incidentDate
    ) throws SQLException {
        insert.setString(1, userName);
        insert.setString(2, title);
        insert.setString(3, description);
        insert.setString(4, status);
        insert.setTimestamp(5, Timestamp.from(createdAt));
        insert.setString(6, category);
        insert.setString(7, priority);
        insert.setString(8, location);
        insert.setString(9, preferredContact);
        insert.setString(10, incidentDate);
        insert.executeUpdate();
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
            String sql = "SELECT id, user_name, title, description, status, created_at, category, priority, location, preferred_contact, incident_date FROM complaints";
            if ("USER".equals(auth.role)) {
                sql += " WHERE user_name = ?";
            }
            sql += " ORDER BY id DESC";

            try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                if ("USER".equals(auth.role)) {
                    statement.setString(1, auth.username);
                }

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        if (!first) {
                            json.append(',');
                        }
                        json.append(Complaint.fromResultSet(rs).toJson());
                        first = false;
                    }
                }
            } catch (SQLException e) {
                sendJson(exchange, 500, "{\"error\":\"Failed to load complaints\"}");
                return;
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

            String sql = "INSERT INTO complaints (user_name, title, description, status, created_at, category, priority, location, preferred_contact, incident_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String normalizedCategory = isBlank(category) ? "General" : category;
            String normalizedPriority = isBlank(priority) ? "MEDIUM" : priority;
            String normalizedLocation = isBlank(location) ? "Not specified" : location;
            String normalizedContact = isBlank(preferredContact) ? "In App" : preferredContact;
            String normalizedIncidentDate = isBlank(incidentDate) ? "Not provided" : incidentDate;
            Instant createdAt = Instant.now();

            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, auth.username);
                statement.setString(2, title);
                statement.setString(3, description);
                statement.setString(4, "OPEN");
                statement.setTimestamp(5, Timestamp.from(createdAt));
                statement.setString(6, normalizedCategory);
                statement.setString(7, normalizedPriority);
                statement.setString(8, normalizedLocation);
                statement.setString(9, normalizedContact);
                statement.setString(10, normalizedIncidentDate);
                statement.executeUpdate();

                long id;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        sendJson(exchange, 500, "{\"error\":\"Unable to create complaint\"}");
                        return;
                    }
                    id = keys.getLong(1);
                }

                Complaint complaint = new Complaint(
                    id,
                    sanitize(auth.username),
                    sanitize(title),
                    sanitize(description),
                    "OPEN",
                    createdAt.toString(),
                    sanitize(normalizedCategory),
                    sanitize(normalizedPriority),
                    sanitize(normalizedLocation),
                    sanitize(normalizedContact),
                    sanitize(normalizedIncidentDate)
                );
                sendJson(exchange, 201, complaint.toJson());
            } catch (SQLException e) {
                sendJson(exchange, 500, "{\"error\":\"Failed to create complaint\"}");
            }
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

            String updateSql = "UPDATE complaints SET status = ? WHERE id = ?";
            String selectSql = "SELECT id, user_name, title, description, status, created_at, category, priority, location, preferred_contact, incident_date FROM complaints WHERE id = ?";
            try (Connection connection = getConnection();
                 PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, status);
                update.setLong(2, id);
                int changed = update.executeUpdate();
                if (changed == 0) {
                    sendJson(exchange, 404, "{\"error\":\"Complaint not found\"}");
                    return;
                }

                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    select.setLong(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            sendJson(exchange, 200, Complaint.fromResultSet(rs).toJson());
                            return;
                        }
                    }
                }
                sendJson(exchange, 404, "{\"error\":\"Complaint not found\"}");
            } catch (SQLException e) {
                sendJson(exchange, 500, "{\"error\":\"Failed to update complaint\"}");
            }
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

            int total = 0;
            try (Connection connection = getConnection();
                 PreparedStatement statusStmt = connection.prepareStatement("SELECT status, COUNT(*) AS c FROM complaints GROUP BY status");
                 PreparedStatement totalStmt = connection.prepareStatement("SELECT COUNT(*) FROM complaints")) {

                try (ResultSet rs = statusStmt.executeQuery()) {
                    while (rs.next()) {
                        counts.put(rs.getString("status"), rs.getInt("c"));
                    }
                }

                try (ResultSet rs = totalStmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                sendJson(exchange, 500, "{\"error\":\"Failed to load analytics\"}");
                return;
            }

            String json = "{" +
                "\"total\":" + total + "," +
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

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static int parsePort(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
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
        private final String status;
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

        private static Complaint fromResultSet(ResultSet rs) throws SQLException {
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            String createdAt = createdAtTs == null ? Instant.now().toString() : createdAtTs.toInstant().toString();
            return new Complaint(
                rs.getLong("id"),
                sanitize(rs.getString("user_name")),
                sanitize(rs.getString("title")),
                sanitize(rs.getString("description")),
                sanitize(rs.getString("status")),
                createdAt,
                sanitize(rs.getString("category")),
                sanitize(rs.getString("priority")),
                sanitize(rs.getString("location")),
                sanitize(rs.getString("preferred_contact")),
                sanitize(rs.getString("incident_date"))
            );
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
