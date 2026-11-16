package app.stocklist.supplieraccount;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the supplier portal (prefix app.supplier). The signing key is separate from the shop app's key, so a
 * supplier token can never be accepted as a shop token and the two can be changed independently.
 *
 * @param publicBaseUrl https address of this server, used in emailed links, e.g. https://api.example.in
 */
@ConfigurationProperties(prefix = "app.supplier")
public record SupplierProperties(
        String jwtSigningKey,
        String jwtKeyId,
        Duration accessTtl,
        Duration refreshTtl,
        Duration verifyLinkTtl,
        Duration resetLinkTtl,
        Duration resendInterval,
        String publicBaseUrl) {

    public SupplierProperties {
        if (jwtKeyId == null || jwtKeyId.isBlank()) jwtKeyId = "s1";
        if (accessTtl == null) accessTtl = Duration.ofMinutes(15);
        if (refreshTtl == null) refreshTtl = Duration.ofDays(30);
        if (verifyLinkTtl == null) verifyLinkTtl = Duration.ofHours(24);
        if (resetLinkTtl == null) resetLinkTtl = Duration.ofHours(1);
        if (resendInterval == null) resendInterval = Duration.ofSeconds(60);
        if (publicBaseUrl == null) publicBaseUrl = "";
        while (publicBaseUrl.endsWith("/")) publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
    }
}
