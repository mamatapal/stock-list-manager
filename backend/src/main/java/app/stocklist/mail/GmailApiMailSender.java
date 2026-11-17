package app.stocklist.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * A {@link org.springframework.mail.javamail.JavaMailSender} that delivers through the Gmail API instead of SMTP.
 * Messages are built exactly as for SMTP (Spring's own conversion from SimpleMailMessage, UTF-8); only the transport
 * changes: the finished MIME message is posted, base64url-encoded, to users.messages.send.
 *
 * <p>A 401 from Gmail discards the access token, renews it once and retries once. Every failure is a
 * {@link MailException}, the type the existing mailers already catch, so callers behave exactly as with SMTP.
 *
 * <p>Batches follow {@link JavaMailSenderImpl}: every message is attempted, failures are collected, and one
 * {@link MailSendException} listing the failed messages (keyed by the original message) is thrown at the end. When
 * the authorization is lost, the remaining messages fail without calling Google, just as an SMTP authentication
 * failure fails the whole batch. A single message rethrows its own exception, so an authorization problem stays a
 * {@link MailAuthenticationException}. The application itself sends one message per call.
 *
 * <p>Each failure is logged once as a WARN line with safe diagnostics only (HTTP status and Google's error codes,
 * {@link GoogleErrors}); never the token, the recipients, the subject, the body or links.
 */
public class GmailApiMailSender extends JavaMailSenderImpl {
    static final URI DEFAULT_API_BASE = URI.create("https://gmail.googleapis.com/");
    private static final Logger log = LoggerFactory.getLogger(GmailApiMailSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final URI sendUri;
    private final GoogleAccessTokens tokens;

    public GmailApiMailSender(HttpClient http, URI apiBase, GoogleAccessTokens tokens) {
        this.http = http;
        this.sendUri = apiBase.resolve("gmail/v1/users/me/messages/send");
        this.tokens = tokens;
        setDefaultEncoding(StandardCharsets.UTF_8.name());
    }

    /** No SMTP server to test; a problem with the Gmail API shows on the first email instead. */
    @Override
    public void testConnection() {
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        Map<Object, Exception> failed = new LinkedHashMap<>();
        for (int i = 0; i < mimeMessages.length; i++) {
            Object original = originalMessages != null ? originalMessages[i] : mimeMessages[i];
            try {
                post(raw(mimeMessages[i]));
            } catch (RuntimeException e) {
                failed.put(original, e);
            }
        }
        if (failed.isEmpty()) return;
        if (mimeMessages.length == 1 && failed.values().iterator().next() instanceof MailException single) {
            throw single;
        }
        throw new MailSendException(failed);
    }

    private static String raw(MimeMessage message) {
        try {
            if (message.getSentDate() == null) message.setSentDate(new Date());
            String messageId = message.getMessageID();
            message.saveChanges();
            if (messageId != null) message.setHeader("Message-ID", messageId);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return Base64.getUrlEncoder().encodeToString(out.toByteArray());
        } catch (MessagingException | IOException e) {
            throw new MailPreparationException("Could not prepare the email (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void post(String raw) {
        String body = "{\"raw\":\"" + raw + "\"}";
        for (int attempt = 1; ; attempt++) {
            String token = tokens.accessToken();
            HttpResponse<String> response = send(token, body);
            int status = response.statusCode();
            if (status >= 200 && status < 300) return;
            String diagnostics = GoogleErrors.gmail(status, response.body(), response.headers());
            if (status == 401) {
                tokens.discard(token);
                if (attempt == 1) continue;
                throw logged(new MailAuthenticationException("Gmail API refused a renewed access token: " + diagnostics));
            }
            throw logged(new MailSendException("Gmail API send failed: " + diagnostics));
        }
    }

    private HttpResponse<String> send(String token, String body) {
        HttpRequest request = HttpRequest.newBuilder(sendUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw logged(new MailSendException("Gmail API send failed: network error (" + e.getClass().getSimpleName() + ")"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Gmail API send was interrupted");
        }
    }

    private static <E extends MailException> E logged(E e) {
        log.warn(e.getMessage());
        return e;
    }
}
