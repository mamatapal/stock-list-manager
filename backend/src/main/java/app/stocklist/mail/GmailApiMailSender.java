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
 */
public class GmailApiMailSender extends JavaMailSenderImpl {
    static final URI DEFAULT_API_BASE = URI.create("https://gmail.googleapis.com/");
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
            int status = send(token, body);
            if (status >= 200 && status < 300) return;
            if (status == 401) {
                tokens.discard(token);
                if (attempt == 1) continue;
                throw new MailAuthenticationException("Gmail API refused a renewed access token (HTTP 401)");
            }
            throw new MailSendException("Gmail API send failed: HTTP " + status);
        }
    }

    private int send(String token, String body) {
        HttpRequest request = HttpRequest.newBuilder(sendUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new MailSendException("Gmail API send failed: network error (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Gmail API send was interrupted");
        }
    }
}
