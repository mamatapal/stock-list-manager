package app.stocklist.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.stocklist.auth.SmtpPasswordResetMailer;
import app.stocklist.supplieraccount.SmtpSupplierMailer;
import app.stocklist.supplieraccount.SupplierOrg;
import app.stocklist.supplieraccount.SupplierProperties;
import app.stocklist.supplieraccount.SupplierUser;
import app.stocklist.user.AppUser;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class GmailApiMailSenderTest {
    static final String CLIENT_ID = "123-test.apps.googleusercontent.com";
    static final String CLIENT_SECRET = "GOCSPX-test-client-secret-value";
    static final String REFRESH_TOKEN = "1//test-refresh-token-value";
    static final String FROM = "Supplier Management <suppliermanagement.mail@gmail.com>";
    static final String SECRET_BODY_TEXT = "secret-body-text-https://test.example/#t=abc";

    private StubGoogle google;
    private MutableClock clock;
    private GoogleAccessTokens tokens;
    private GmailApiMailSender sender;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger root;
    private ch.qos.logback.classic.Logger mailLogger;
    private Level mailLoggerLevel;

    @BeforeEach
    void setUp() throws Exception {
        google = new StubGoogle();
        clock = new MutableClock();
        HttpClient http = HttpClient.newHttpClient();
        tokens = new GoogleAccessTokens(http, google.tokenUri(), CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, clock);
        sender = new GmailApiMailSender(http, google.base(), tokens);
        root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        mailLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("app.stocklist");
        mailLoggerLevel = mailLogger.getLevel();
        mailLogger.setLevel(Level.TRACE);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(logs);
        mailLogger.setLevel(mailLoggerLevel);
        google.close();
    }

    private static SimpleMailMessage message(String text) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(FROM);
        m.setTo("supplier@example.com");
        m.setSubject("अपना ईमेल पक्का करें / Verify your email");
        m.setText(text);
        return m;
    }

    private static MimeMessage parse(byte[] bytes) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
    }

    private MimeMessage lastSent() throws Exception {
        return parse(Base64.getUrlDecoder().decode(google.raws.get(google.raws.size() - 1)));
    }

    // ------------------------------------------------------------------ the message itself

    @Test
    void sendsTheMimeMessageToGmailWithTheAccessToken() throws Exception {
        sender.send(message("नमस्ते, यह परीक्षण है। Hello, this is a test."));

        assertThat(google.sendCalls.get()).isEqualTo(1);
        assertThat(google.authorizations).containsExactly("Bearer AT-1");
        assertThat(google.tokenForms.get(0)).contains("grant_type=refresh_token")
                .contains("refresh_token=" + java.net.URLEncoder.encode(REFRESH_TOKEN, java.nio.charset.StandardCharsets.UTF_8));
        MimeMessage sent = lastSent();
        assertThat(sent.getSubject()).isEqualTo("अपना ईमेल पक्का करें / Verify your email");
        assertThat(sent.getFrom()[0].toString()).contains("suppliermanagement.mail@gmail.com");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("supplier@example.com");
        assertThat(sent.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat((String) sent.getContent()).isEqualTo("नमस्ते, यह परीक्षण है। Hello, this is a test.");
    }

    /** SMTP path: the same Spring conversion as Boot's SMTP sender (UTF-8), captured instead of sent. */
    static final class CapturingSmtpSender extends JavaMailSenderImpl {
        final List<byte[]> sent = new ArrayList<>();

        CapturingSmtpSender() {
            setDefaultEncoding("UTF-8");
        }

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            try {
                for (MimeMessage m : mimeMessages) {
                    m.saveChanges();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    m.writeTo(out);
                    sent.add(out.toByteArray());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    void allSixEmailFlowsArriveExactlyAsTheSmtpPathWouldSendThem() throws Exception {
        SupplierProperties props = new SupplierProperties(null, null, null, null, null, null, null,
                "https://test.example");
        SupplierUser user = new SupplierUser(UUID.randomUUID(), "supplier@example.com", "राम कुमार", "9876543210",
                "hash", Instant.now());
        SupplierOrg org = mock(SupplierOrg.class);
        when(org.getStatus()).thenReturn(SupplierOrg.APPROVED);
        when(org.getLegalName()).thenReturn("अग्रवाल होलसेल / Agarwal Wholesale");
        AppUser owner = mock(AppUser.class);
        when(owner.getEmail()).thenReturn("owner@example.com");
        when(owner.getFullName()).thenReturn("Asha Sharma");
        when(owner.getUsername()).thenReturn("asha");
        String shopFrom = "Stock List Manager <suppliermanagement.mail@gmail.com>";

        List<Consumer<JavaMailSender>> flows = List.of(
                s -> new SmtpSupplierMailer(s, FROM, props).verificationLink(user, "https://test.example/supplier/verify-email#t=v1"),
                s -> new SmtpSupplierMailer(s, FROM, props).passwordResetLink(user, "https://test.example/supplier/reset-password#t=r1"),
                s -> new SmtpSupplierMailer(s, FROM, props).alreadyRegistered(user, "https://test.example/supplier/reset-password#t=r2"),
                s -> new SmtpSupplierMailer(s, FROM, props).passwordChanged(user),
                s -> new SmtpSupplierMailer(s, FROM, props).statusChanged(user, org),
                s -> new SmtpPasswordResetMailer(s, shopFrom, Duration.ofMinutes(30)).send(owner, "CODE-123"));

        for (int i = 0; i < flows.size(); i++) {
            CapturingSmtpSender smtp = new CapturingSmtpSender();
            flows.get(i).accept(smtp);
            flows.get(i).accept(sender);
            assertThat(smtp.sent).as("flow %d via SMTP", i).hasSize(1);
            assertThat(google.raws).as("flow %d via Gmail API", i).hasSize(i + 1);

            MimeMessage expected = parse(smtp.sent.get(0));
            MimeMessage actual = lastSent();
            assertThat(actual.getSubject()).as("subject of flow %d", i).isEqualTo(expected.getSubject());
            assertThat(actual.getFrom()).as("From of flow %d", i).isEqualTo(expected.getFrom());
            assertThat(actual.getAllRecipients()).as("To of flow %d", i).isEqualTo(expected.getAllRecipients());
            assertThat(actual.getContentType()).as("content type of flow %d", i).isEqualTo(expected.getContentType());
            assertThat((String) actual.getContent()).as("body of flow %d", i).isEqualTo((String) expected.getContent());
            if (i < 5) assertThat((String) actual.getContent()).contains("नमस्ते").contains("Hello");
        }
    }

    // ------------------------------------------------------------------ on-demand renewal

    @Test
    void nothingIsCalledUntilTheFirstEmail() {
        assertThat(google.tokenCalls.get()).isZero();
        assertThat(google.sendCalls.get()).isZero();
        assertThat(tokens.authorizationLost()).isFalse();
    }

    @Test
    void theAccessTokenIsReusedAndRenewedOnlyWhenNearlyExpired() {
        sender.send(message("one"));
        sender.send(message("two"));
        assertThat(google.tokenCalls.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(3600 - 120));
        sender.send(message("three"));
        assertThat(google.tokenCalls.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(90)); // less than a minute left
        sender.send(message("four"));
        assertThat(google.tokenCalls.get()).isEqualTo(2);
        assertThat(google.authorizations).containsExactly("Bearer AT-1", "Bearer AT-1", "Bearer AT-1", "Bearer AT-2");
    }

    @Test
    void concurrentEmailsCauseOneRenewal() throws Exception {
        google.tokenDelayMillis = 300;
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int k = i;
            results.add(pool.submit(() -> {
                start.await();
                sender.send(message("parallel " + k));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : results) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(google.tokenCalls.get()).isEqualTo(1);
        assertThat(google.sendCalls.get()).isEqualTo(n);
    }

    @Test
    void a401RenewsTheTokenOnceAndRetriesOnce() {
        google.sendStatus = n -> n == 1 ? 401 : 200;
        sender.send(message("retry"));
        assertThat(google.tokenCalls.get()).isEqualTo(2);
        assertThat(google.sendCalls.get()).isEqualTo(2);
        assertThat(google.authorizations).containsExactly("Bearer AT-1", "Bearer AT-2");
    }

    @Test
    void aSecond401FailsWithoutAThirdAttempt() {
        google.sendStatus = n -> 401;
        assertThatThrownBy(() -> sender.send(message("x"))).isInstanceOf(MailAuthenticationException.class);
        assertThat(google.sendCalls.get()).isEqualTo(2);
        assertThat(google.tokenCalls.get()).isEqualTo(2);
        assertThat(tokens.authorizationLost()).isFalse();
    }

    // ------------------------------------------------------------------ invalid_grant

    @Test
    void invalidGrantStopsCallsAndLogsAtMostOnceAnHour() {
        google.tokenReply = n -> new StubGoogle.Reply(400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");

        assertThatThrownBy(() -> sender.send(message("a"))).isInstanceOf(MailAuthenticationException.class);
        assertThat(tokens.authorizationLost()).isTrue();
        assertThat(lostLogLines()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(59));
        assertThatThrownBy(() -> sender.send(message("b"))).isInstanceOf(MailAuthenticationException.class);
        assertThat(lostLogLines()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(2));
        assertThatThrownBy(() -> sender.send(message("c"))).isInstanceOf(MailAuthenticationException.class);
        assertThat(lostLogLines()).isEqualTo(2);

        assertThat(google.tokenCalls.get()).as("no call to Google after invalid_grant").isEqualTo(1);
        assertThat(google.sendCalls.get()).isZero();
    }

    private long lostLogLines() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().equals(GoogleAccessTokens.LOST_LOG))
                .count();
    }

    @Test
    void serverErrorsAndRateLimitsFailOnlyThatEmail() {
        google.sendStatus = n -> n == 1 ? 500 : n == 2 ? 429 : 200;
        assertThatThrownBy(() -> sender.send(message("a"))).isInstanceOf(MailSendException.class);
        assertThatThrownBy(() -> sender.send(message("b"))).isInstanceOf(MailSendException.class);
        sender.send(message("c"));
        assertThat(tokens.authorizationLost()).isFalse();
        assertThat(google.sendCalls.get()).isEqualTo(3);
        assertThat(google.tokenCalls.get()).isEqualTo(1);
    }

    @Test
    void aTokenEndpointErrorIsNotTreatedAsLostAuthorization() {
        google.tokenReply = n -> n == 1 ? new StubGoogle.Reply(503, "{\"error\":\"backend_error\"}")
                : new StubGoogle.Reply(200, "{\"access_token\":\"AT-" + n + "\",\"expires_in\":3600}");
        assertThatThrownBy(() -> sender.send(message("a"))).isInstanceOf(MailSendException.class);
        assertThat(tokens.authorizationLost()).isFalse();
        sender.send(message("b"));
        assertThat(google.tokenCalls.get()).isEqualTo(2);
        assertThat(google.sendCalls.get()).isEqualTo(1);
    }

    @Test
    void aNetworkErrorIsNotTreatedAsLostAuthorization() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        GoogleAccessTokens offline = new GoogleAccessTokens(http, URI.create("http://127.0.0.1:" + closedPort + "/token"),
                CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, clock);
        GmailApiMailSender offlineSender = new GmailApiMailSender(http, google.base(), offline);
        assertThatThrownBy(() -> offlineSender.send(message("a"))).isInstanceOf(MailSendException.class);
        assertThat(offline.authorizationLost()).isFalse();
    }

    // ------------------------------------------------------------------ nothing secret in logs or errors

    @Test
    void secretsTokensAndBodiesNeverAppearInLogsOrErrors() {
        List<String> errors = new ArrayList<>();
        Runnable[] scenarios = {
                () -> sender.send(message(SECRET_BODY_TEXT)),
                () -> { google.sendStatus = n -> 500; sender.send(message(SECRET_BODY_TEXT)); },
                () -> { google.sendStatus = n -> 401; sender.send(message(SECRET_BODY_TEXT)); },
                () -> {
                    google.tokenReply = n -> new StubGoogle.Reply(400, "{\"error\":\"invalid_grant\"}");
                    tokens.discard("AT-1");
                    tokens.discard("AT-2");
                    tokens.discard("AT-3");
                    tokens.discard("AT-4");
                    tokens.discard("AT-5");
                    clock.advance(Duration.ofHours(2));
                    sender.send(message(SECRET_BODY_TEXT));
                },
                () -> sender.send(message(SECRET_BODY_TEXT)),
        };
        for (Runnable scenario : scenarios) {
            try {
                scenario.run();
            } catch (MailException e) {
                errors.add(String.valueOf(e));
                errors.add(String.valueOf(e.getMessage()));
                if (e.getCause() != null) errors.add(String.valueOf(e.getCause()));
            }
        }
        assertThat(errors).isNotEmpty();
        List<String> logged = new ArrayList<>();
        for (ILoggingEvent e : logs.list) {
            logged.add(e.getFormattedMessage());
            if (e.getThrowableProxy() != null) logged.add(e.getThrowableProxy().getMessage());
        }
        assertThat(logged).anyMatch(GoogleAccessTokens.LOST_LOG::equals);
        for (String text : concat(errors, logged)) {
            assertThat(String.valueOf(text))
                    .doesNotContain(CLIENT_SECRET)
                    .doesNotContain(REFRESH_TOKEN)
                    .doesNotContain("AT-1").doesNotContain("AT-2").doesNotContain("AT-3")
                    .doesNotContain("secret-body-text");
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    @Test
    void propertiesNeverShowTheSecretOrTheRefreshToken() {
        MailProviderProperties.Gmail g = new MailProviderProperties.Gmail("suppliermanagement.mail@gmail.com", null,
                CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, null, null);
        assertThat(g.toString()).doesNotContain(CLIENT_SECRET).doesNotContain(REFRESH_TOKEN);
        assertThat(new MailProviderProperties(null, g).toString()).doesNotContain(CLIENT_SECRET)
                .doesNotContain(REFRESH_TOKEN);
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
