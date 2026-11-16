package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.stocklist.platformadmin.AdminAuthService;
import app.stocklist.platformadmin.Totp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Helpers for the Supplier Management and Supplier Administration integration tests (Phase S1). */
abstract class SupplierTestSupport extends IntegrationTestBase {
    static final String SUPPLIER_PW = "Blue-Kettle-77";
    static final String ADMIN_PW = "Admin-Long-Pass-2026";
    static final String PORTAL = "/api/v1/supplier-portal";
    private static final HttpClient RAW = HttpClient.newHttpClient();

    @Autowired
    protected CapturingSupplierMailer supplierMail;
    @Autowired
    protected AdminAuthService adminAuth;

    // ----------------------------------------------------------------- supplier side

    static String supplierEmail() {
        return unique("sup") + "@example.com";
    }

    static String registerJson(String email, String legalName, String mobile, String gstin, String password) {
        return """
                {"legalName":"%s","tradeName":"Agarwal Traders","contactPerson":"Ramesh Agarwal","mobile":"%s",
                 "email":"%s","password":"%s","address":"12 Station Road","city":"Indore","state":"Madhya Pradesh",
                 "pinCode":"452001","gstin":%s}
                """.formatted(legalName, mobile, email, password, gstin == null ? "null" : "\"" + gstin + "\"");
    }

    protected Resp registerSupplier(String email) {
        return post(PORTAL + "/auth/register", registerJson(email, "Agarwal Wholesale Pvt Ltd", "9876543210",
                "23AAPCA1234B1Z5", SUPPLIER_PW));
    }

    /** The token part of an emailed link ("...#t=TOKEN"). */
    static String token(String link) {
        assertNotNull(link, "no link was emailed");
        return link.substring(link.indexOf("#t=") + 3);
    }

    protected Resp verify(String token) {
        return post(PORTAL + "/auth/verify-email", "{\"token\":\"" + token + "\"}");
    }

    protected Resp supplierLogin(String email, String password) {
        return post(PORTAL + "/auth/login", """
                {"email":"%s","password":"%s","deviceId":"%s","deviceName":"Supplier phone"}
                """.formatted(email, password, deviceId()));
    }

    /** Registers, verifies the email and signs in; returns the session (business waiting for approval). */
    protected Resp verifiedSupplier(String email) {
        assertEquals(202, registerSupplier(email).status());
        assertEquals(200, verify(token(supplierMail.verifyLinks.get(email))).status());
        Resp s = supplierLogin(email, SUPPLIER_PW);
        assertEquals(200, s.status(), s.raw());
        return s;
    }

    protected Resp supplierMe(String accessToken) {
        return get(PORTAL + "/me", accessToken);
    }

    // ----------------------------------------------------------------- admin side

    record Admin(String username, String secret) {
        String code(Instant at) {
            return Totp.code(secret, Totp.step(at));
        }

        String codeNow() {
            return code(Instant.now());
        }
    }

    record Http(int status, JsonNode body, String raw, Map<String, java.util.List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0)).findFirst().orElse(null);
        }

        String str(String field) {
            return text(body.get(field));
        }
    }

    protected Admin newAdmin() {
        String username = unique("adm");
        return new Admin(username, adminAuth.createAdmin(username, "Admin " + username, null, ADMIN_PW));
    }

    /** Raw HTTP so headers (Set-Cookie, CSP) can be checked. csrf=false leaves out X-Requested-With. */
    protected Http http(String method, String path, String json, String cookie, boolean csrf, Long ifMatch) {
        try {
            int port = Integer.parseInt(env.getProperty("local.server.port"));
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "application/json");
            if (json != null) b.header("Content-Type", "application/json");
            if (cookie != null) b.header("Cookie", "SA_SESSION=" + cookie);
            if (csrf) b.header("X-Requested-With", "SupplierAdministration");
            if (ifMatch != null) b.header("If-Match", ifMatch.toString());
            b.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> res = RAW.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node;
            try {
                node = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
            } catch (RuntimeException notJson) {
                node = JSON.createObjectNode();
            }
            return new Http(res.statusCode(), node, res.body(), res.headers().map());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Http adminLogin(Admin a, String code) {
        return http("POST", "/api/v1/admin/auth/login", """
                {"username":"%s","password":"%s","code":"%s"}""".formatted(a.username(), ADMIN_PW, code),
                null, true, null);
    }

    protected String signIn(Admin a) {
        Http r = adminLogin(a, a.codeNow());
        assertEquals(200, r.status(), r.raw());
        return cookieValue(r.header("Set-Cookie"));
    }

    static String cookieValue(String setCookie) {
        assertNotNull(setCookie, "no Set-Cookie header");
        String first = setCookie.split(";")[0];
        return first.substring(first.indexOf('=') + 1);
    }

    protected Http adminGet(String path, String cookie) {
        return http("GET", path, null, cookie, true, null);
    }

    protected Http adminPost(String path, String json, String cookie, long version) {
        return http("POST", path, json, cookie, true, version);
    }

    protected String orgId(Resp session) {
        return text(session.at("/me/org/id"));
    }

    protected long orgVersion(String cookie, String orgId) {
        Http d = adminGet("/api/v1/admin/suppliers/" + orgId, cookie);
        assertEquals(200, d.status(), d.raw());
        return Long.parseLong(text(d.body().at("/summary/version")));
    }
}
