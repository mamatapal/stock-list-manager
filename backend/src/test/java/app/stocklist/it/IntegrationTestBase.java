package app.stocklist.it;

import app.stocklist.TestSupport;
import app.stocklist.auth.PasswordResetMailer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the real application on a random port against a real MySQL 8.4 in Docker (Testcontainers).
 * If Docker is not running these tests are reported as skipped, not passed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestBase.TestMailerConfig.class)
public abstract class IntegrationTestBase {
    static final boolean DOCKER = dockerAvailable();
    static final MySQLContainer MYSQL = DOCKER ? new MySQLContainer("mysql:8.4") : null;

    static {
        if (DOCKER) {
            MYSQL.start();
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(DOCKER, "Docker is not running: MySQL integration tests skipped");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        if (DOCKER) {
            r.add("spring.datasource.url", MYSQL::getJdbcUrl);
            r.add("spring.datasource.username", MYSQL::getUsername);
            r.add("spring.datasource.password", MYSQL::getPassword);
        }
        r.add("app.jwt.signing-key", () -> TestSupport.SIGNING_KEY);
        r.add("app.login-rate-per-minute", () -> 10_000);
        r.add("springdoc.api-docs.enabled", () -> true);
        r.add("springdoc.swagger-ui.enabled", () -> true);
    }

    @Autowired
    protected Environment env;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected CapturingMailer mailer;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    // ----------------------------------------------------------------- HTTP helpers

    public record Resp(int status, JsonNode body, String raw) {
        public String str(String field) {
            return text(body.get(field));
        }

        public JsonNode at(String pointer) {
            return body.at(pointer);
        }
    }

    /** Value of a JSON scalar without its quotes (avoids depending on Jackson 3's renamed text accessors). */
    public static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String s = node.toString();
        return s.startsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    protected Resp call(String method, String path, String json, String bearer, String stepUp) {
        return callWithHeader(method, path, json, bearer, stepUp == null ? null : "X-Step-Up-Token", stepUp);
    }

    protected Resp callWithHeader(String method, String path, String json, String bearer, String header, String value) {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        if (header != null && value != null) h.put(header, value);
        return callWithHeaders(method, path, json, bearer, h);
    }

    protected Resp callWithHeaders(String method, String path, String json, String bearer, java.util.Map<String, String> headers) {
        try {
            int port = Integer.parseInt(env.getProperty("local.server.port"));
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (bearer != null) b.header("Authorization", "Bearer " + bearer);
            headers.forEach(b::header);
            b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String raw = res.body();
            JsonNode node;
            try {
                node = raw == null || raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
            } catch (RuntimeException notJson) {
                node = JSON.createObjectNode(); // e.g. the Swagger UI HTML page
            }
            return new Resp(res.statusCode(), node, raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Resp post(String path, String json) { return call("POST", path, json, null, null); }
    protected Resp post(String path, String json, String bearer) { return call("POST", path, json, bearer, null); }
    protected Resp post(String path, String json, String bearer, String stepUp) { return call("POST", path, json, bearer, stepUp); }
    protected Resp get(String path, String bearer) { return call("GET", path, null, bearer, null); }
    protected Resp put(String path, String json, String bearer, String stepUp) { return call("PUT", path, json, bearer, stepUp); }
    protected Resp delete(String path, String bearer, String stepUp) { return call("DELETE", path, null, bearer, stepUp); }

    // ----------------------------------------------------------------- domain helpers

    protected static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected static String deviceId() {
        return UUID.randomUUID().toString();
    }

    /** Registers a new business; returns the session response. */
    protected Resp registerBusiness(String username, String password) {
        String json = """
                {"businessName":"%s Stores","ownerName":"Owner %s","username":"%s","mobile":"9876543210",
                 "email":"%s@example.com","password":"%s","address":"12 MG Road","city":"Pune",
                 "state":"Maharashtra","pinCode":"411001","gstin":null,"deviceId":"%s","deviceName":"Test phone"}
                """.formatted(username, username, username, username, password, deviceId());
        return post("/api/v1/auth/register-business", json);
    }

    protected Resp login(String username, String password) {
        return login(username, password, deviceId());
    }

    protected Resp login(String username, String password, String deviceId) {
        return post("/api/v1/auth/login", """
                {"username":"%s","password":"%s","deviceId":"%s","deviceName":"Test phone"}
                """.formatted(username, password, deviceId));
    }

    protected Resp loginAs(String role, String username, String password, String deviceId, String deviceName) {
        return post("/api/v1/auth/login", """
                {"username":"%s","password":"%s","deviceId":"%s","deviceName":"%s","expectedRole":"%s"}
                """.formatted(username, password, deviceId, deviceName, role));
    }

    protected String stepUp(String accessToken, String password) {
        Resp r = post("/api/v1/auth/reauth", "{\"password\":\"%s\"}".formatted(password), accessToken);
        if (r.status() != 200) throw new AssertionError("reauth failed: " + r.raw());
        return r.str("stepUpToken");
    }

    protected static final String STAFF_PW = "Staff-Pass-2026";

    /** Signs a new staff member in with the temporary password and sets STAFF_PW; returns the new session. */
    protected Resp staffSession(String username, String temporaryPassword) {
        Resp first = login(username, temporaryPassword);
        if (first.status() != 200) throw new AssertionError("staff login failed: " + first.raw());
        Resp changed = post("/api/v1/auth/password/change",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(temporaryPassword, STAFF_PW),
                first.str("accessToken"));
        if (changed.status() != 200) throw new AssertionError("password change failed: " + changed.raw());
        return changed;
    }

    protected Resp createStaff(String ownerToken, String stepUp, String username, String permissionsJson) {
        String perms = permissionsJson == null ? "" : ",\"permissions\":" + permissionsJson;
        return post("/api/v1/staff", "{\"fullName\":\"Staff %s\",\"username\":\"%s\"%s}".formatted(username, username,
                perms), ownerToken, stepUp);
    }

    // ----------------------------------------------------------------- test doubles

    public static class CapturingMailer implements PasswordResetMailer {
        public final Map<String, String> lastTokenByUsername = new ConcurrentHashMap<>();

        @Override
        public void send(app.stocklist.user.AppUser user, String rawToken) {
            lastTokenByUsername.put(user.getUsername(), rawToken);
        }
    }

    @TestConfiguration
    static class TestMailerConfig {
        @Bean
        @Primary
        CapturingMailer capturingMailer() {
            return new CapturingMailer();
        }
    }
}
