package app.stocklist.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The deploy and the server's monitoring wait for this page. It must say UP on a server where SMTP is not set
 * up (SMTP_HOST empty), because password-reset email is optional.
 */
class HealthIT extends IntegrationTestBase {
    @Test
    void healthIsUpWithoutSmtp() {
        assertTrue(env.getProperty("spring.mail.host", "").isBlank(), "this test needs SMTP_HOST to be empty");
        Resp health = get("/actuator/health", null);
        assertEquals(200, health.status(), health.raw());
        assertTrue(health.raw().contains("\"UP\""), health.raw());
    }
}
