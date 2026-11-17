package app.stocklist.mail;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

/**
 * Short-lived Gmail API access tokens, obtained from the stored refresh token only when an email is about to be sent.
 * There is no timer and no call at startup. The access token lives in memory only.
 *
 * <ul>
 *   <li>Single-flight: callers wait on one lock, so several emails needing a new token cause one renewal request.</li>
 *   <li>{@code invalid_grant} (access revoked, unused for six months, account password changed) puts this object in
 *       the "authorization lost" state: no more calls to Google until the backend is recreated with a new token, and
 *       one ERROR line at most once an hour.</li>
 *   <li>Other failures (network, 5xx, 429) fail only that email; the next email tries again.</li>
 * </ul>
 * Failures are logged once each as a WARN line with safe diagnostics only ({@link GoogleErrors}): HTTP status and
 * Google's error code. Tokens, the client secret and free-text error descriptions are never logged or put in
 * exception messages.
 *
 * <p>All state is per instance and in memory: a new backend process (container recreated with a new refresh token)
 * starts with no access token and without the "authorization lost" state.
 */
public class GoogleAccessTokens {
    static final URI DEFAULT_TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    static final String LOST_LOG = "Gmail API authorization lost (invalid_grant). Emails are not being sent. Re-run"
            + " tools/windows/gmail-authorize.ps1, then recreate the backend container (docs/EMAIL_SETUP.md).";
    private static final Logger log = LoggerFactory.getLogger(GoogleAccessTokens.class);
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(60);
    private static final Duration LOG_INTERVAL = Duration.ofHours(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http;
    private final URI tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final Clock clock;

    private record Token(String value, Instant expiresAt) {}

    // All guarded by "this".
    private Token current;
    private boolean authorizationLost;
    private Instant lastLostLog;

    public GoogleAccessTokens(HttpClient http, URI tokenUri, String clientId, String clientSecret,
                              String refreshToken, Clock clock) {
        this.http = http;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.clock = clock;
    }

    /** A valid access token, renewed first if it has less than a minute left. */
    public synchronized String accessToken() {
        if (authorizationLost) {
            reportLost();
            throw new MailAuthenticationException("Gmail API authorization lost (invalid_grant)");
        }
        if (current != null && current.expiresAt().isAfter(clock.instant().plus(RENEW_BEFORE_EXPIRY))) {
            return current.value();
        }
        current = renew();
        return current.value();
    }

    /** Forget a token Gmail refused (401), so the next call renews it. */
    public synchronized void discard(String token) {
        if (current != null && current.value().equals(token)) current = null;
    }

    public synchronized boolean authorizationLost() {
        return authorizationLost;
    }

    private Token renew() {
        String form = "grant_type=refresh_token"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken);
        HttpRequest request = HttpRequest.newBuilder(tokenUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw failure("network error (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Gmail API token renewal was interrupted");
        }
        int status = response.statusCode();
        Map<?, ?> json = GoogleErrors.parse(response.body());
        if (status == 200) {
            Object accessToken = json.get("access_token");
            if (!(accessToken instanceof String value) || value.isBlank()) {
                throw failure("HTTP 200 without an access token");
            }
            long seconds = json.get("expires_in") instanceof Number n ? n.longValue() : 3600;
            return new Token(value, clock.instant().plusSeconds(seconds));
        }
        if ((status == 400 || status == 401) && "invalid_grant".equals(json.get("error"))) {
            authorizationLost = true;
            lastLostLog = null;
            reportLost();
            throw new MailAuthenticationException("Gmail API authorization lost (invalid_grant)");
        }
        throw failure(GoogleErrors.token(status, json));
    }

    private static MailSendException failure(String diagnostics) {
        String message = "Gmail API token renewal failed: " + diagnostics;
        log.warn(message);
        return new MailSendException(message);
    }

    private void reportLost() {
        Instant now = clock.instant();
        if (lastLostLog == null || !now.isBefore(lastLostLog.plus(LOG_INTERVAL))) {
            log.error(LOST_LOG);
            lastLostLog = now;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
