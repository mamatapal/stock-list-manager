package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Uses the generated OpenAPI document as the list of every endpoint, so a newly added endpoint is
 * automatically checked for authentication.
 */
class OpenApiIT extends IntegrationTestBase {
    private static final Set<String> PUBLIC = Set.of(
            "POST /api/v1/auth/register-business",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/password/forgot",
            "POST /api/v1/auth/password/reset");

    private List<String[]> endpoints(JsonNode doc) {
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : doc.get("paths").properties()) {
            for (Map.Entry<String, JsonNode> op : path.getValue().properties()) {
                list.add(new String[]{op.getKey().toUpperCase(), path.getKey()});
            }
        }
        return list;
    }

    @Test
    void documentationIsServedWithSecuritySchemes() {
        Resp docs = get("/v3/api-docs", null);
        assertEquals(200, docs.status(), docs.raw());
        assertTrue(docs.raw().contains("/api/v1/auth/login"));
        assertTrue(docs.raw().contains("/api/v1/staff/{id}/permissions"));
        assertFalse(docs.at("/components/securitySchemes/bearerAuth").isMissingNode());
        assertFalse(docs.at("/components/securitySchemes/stepUp").isMissingNode());
        Resp ui = call("GET", "/swagger-ui/index.html", null, null, null);
        assertEquals(200, ui.status(), "Swagger UI page");
    }

    /** Writes the API description to target/openapi.json; the deployment package ships it as API documentation. */
    @Test
    void exportsTheApiDocument() throws Exception {
        Resp docs = get("/v3/api-docs", null);
        assertEquals(200, docs.status());
        java.nio.file.Path out = java.nio.file.Path.of("target", "openapi.json");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(docs.body()));
        assertTrue(java.nio.file.Files.size(out) > 10_000);
    }

    @Test
    void everyProtectedEndpointRejectsMissingAndForgedTokens() {
        Resp docs = get("/v3/api-docs", null);
        List<String[]> eps = endpoints(docs.body());
        assertTrue(eps.size() >= 20, "expected the full Phase 1 API, found " + eps.size());
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl";
        int checked = 0;
        for (String[] ep : eps) {
            String key = ep[0] + " " + ep[1];
            if (PUBLIC.contains(key)) continue;
            String path = ep[1].replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            String body = ep[0].equals("GET") || ep[0].equals("DELETE") ? null : "{}";
            assertEquals(401, call(ep[0], path, body, null, null).status(), key + " without a token");
            assertEquals(401, call(ep[0], path, body, forged, null).status(), key + " with a forged token");
            checked++;
        }
        assertTrue(checked >= 15, "checked " + checked);
    }

    @Test
    void staffWithDefaultPermissionsIsRefusedOnEveryStaffManagementEndpoint() {
        String owner = unique("own");
        String ownerToken = registerBusiness(owner, "Green-Mango-42").str("accessToken");
        Resp created = createStaff(ownerToken, stepUp(ownerToken, "Green-Mango-42"), unique("st"), null);
        String temp = created.str("temporaryPassword");
        String staffToken = staffSession(text(created.at("/staff/username")), temp).str("accessToken");
        String staffStep = stepUp(staffToken, STAFF_PW);

        Resp docs = get("/v3/api-docs", null);
        int checked = 0;
        for (String[] ep : endpoints(docs.body())) {
            if (!ep[1].startsWith("/api/v1/staff") && !ep[1].startsWith("/api/v1/activity")
                    && !(ep[1].equals("/api/v1/business") && ep[0].equals("PUT"))) continue;
            String path = ep[1].replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            String body = ep[0].equals("GET") || ep[0].equals("DELETE") ? null
                    : "{\"fullName\":\"x\",\"username\":\"xyz123\",\"permissions\":[],\"name\":\"x\",\"address\":\"x\","
                      + "\"city\":\"x\",\"state\":\"x\",\"pinCode\":\"411001\",\"phone\":\"9876543210\",\"email\":\"x@example.com\"}";
            int status = call(ep[0], path, body, staffToken, staffStep).status();
            assertEquals(403, status, ep[0] + " " + ep[1] + " must be refused for default staff");
            checked++;
        }
        assertTrue(checked >= 10, "checked " + checked);
    }
}
