package app.stocklist.platformadmin;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Supplier Administration portal (prefix app.admin).
 *
 * @param totpKey     Base64 of 32 random bytes (ADMIN_TOTP_KEY); encrypts the administrators' authenticator secrets
 * @param sessionIdle a portal session ends after this long without a request
 * @param sessionMax  and in any case this long after sign-in
 * @param secureCookie false only for local development over plain http
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String totpKey, Duration sessionIdle, Duration sessionMax, Boolean secureCookie) {
    public AdminProperties {
        if (sessionIdle == null) sessionIdle = Duration.ofMinutes(30);
        if (sessionMax == null) sessionMax = Duration.ofHours(8);
        if (secureCookie == null) secureCookie = Boolean.TRUE;
    }
}
