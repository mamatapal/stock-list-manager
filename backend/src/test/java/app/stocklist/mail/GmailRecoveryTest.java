package app.stocklist.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.stocklist.auth.MailConfig;
import app.stocklist.auth.PasswordResetMailer;
import app.stocklist.config.AppProperties;
import app.stocklist.supplieraccount.SupplierMailConfig;
import app.stocklist.supplieraccount.SupplierMailer;
import app.stocklist.supplieraccount.SupplierProperties;
import app.stocklist.supplieraccount.SupplierUser;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Recovery after invalid_grant, as an operator does it (docs/EMAIL_SETUP.md, "When it stops working"):
 * authorization is lost, the helper installs a new refresh token in .env, the backend container is recreated (a new
 * process, so a new Spring context reading the new value), and email works again. Nothing from the old process can
 * be reused: the token state is per instance, and no mail class keeps mutable static state.
 */
class GmailRecoveryTest {
    static final String GMAIL = "suppliermanagement.mail@gmail.com";
    static final String OLD_TOKEN = "1//old-refresh-token-revoked";
    static final String NEW_TOKEN = "1//new-refresh-token-installed";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MailProviderProperties.class, AppProperties.class, SupplierProperties.class})
    static class Props {}

    private StubGoogle google;

    @BeforeEach
    void setUp() throws Exception {
        google = new StubGoogle();
        // Google refuses the old refresh token (revoked) and accepts the new one.
        google.tokenReply = n -> google.tokenForms.get(n - 1).contains("refresh_token=" + enc(OLD_TOKEN))
                ? new StubGoogle.Reply(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}")
                : new StubGoogle.Reply(200, "{\"access_token\":\"AT-" + n + "\",\"expires_in\":3600}");
    }

    @AfterEach
    void tearDown() {
        google.close();
    }

    /** One backend process: the configuration it reads from .env when its container starts. */
    private ApplicationContextRunner backendWith(String refreshToken) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(Props.class, MailProviderConfig.class, MailConfig.class, SupplierMailConfig.class)
                .withPropertyValues(
                        "app.reset-token-ttl=30m", "app.login-rate-per-minute=20", "app.mail-log-tokens=false",
                        "app.supplier.public-base-url=https://test.example",
                        "app.mail.provider=gmail-api",
                        "app.mail.gmail.sender=" + GMAIL,
                        "app.mail.gmail.client-id=123-test.apps.googleusercontent.com",
                        "app.mail.gmail.client-secret=GOCSPX-test",
                        "app.mail.gmail.refresh-token=" + refreshToken,
                        "app.mail.gmail.token-uri=" + google.tokenUri(),
                        "app.mail.gmail.api-base-uri=" + google.base(),
                        "app.mail-from=Stock List Manager <" + GMAIL + ">",
                        "app.supplier.mail-from=Supplier Management <" + GMAIL + ">");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static SimpleMailMessage message() {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(GMAIL);
        m.setTo("supplier@example.com");
        m.setSubject("test");
        m.setText("test");
        return m;
    }

    private static GoogleAccessTokens tokensOf(JavaMailSender sender) {
        return (GoogleAccessTokens) ReflectionTestUtils.getField(sender, "tokens");
    }

    @Test
    void afterANewRefreshTokenAndARecreatedContainerEmailWorksAgain() throws Exception {
        AtomicReference<Object> oldSender = new AtomicReference<>();
        AtomicReference<GoogleAccessTokens> oldTokens = new AtomicReference<>();
        SupplierUser user = new SupplierUser(UUID.randomUUID(), "supplier@example.com", "राम कुमार", "9876543210",
                "hash", Instant.now());

        // 1. The running backend: Google withdrew the authorization.
        backendWith(OLD_TOKEN).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            JavaMailSender sender = ctx.getBean(JavaMailSender.class);
            assertThatThrownBy(() -> sender.send(message())).isInstanceOf(MailAuthenticationException.class);
            assertThat(tokensOf(sender).authorizationLost()).isTrue();
            // Later emails fail at once without calling Google; the mailers log and carry on, as before.
            ctx.getBean(SupplierMailer.class).passwordChanged(user);
            assertThatThrownBy(() -> sender.send(message())).isInstanceOf(MailAuthenticationException.class);
            oldSender.set(sender);
            oldTokens.set(tokensOf(sender));
        });
        assertThat(google.tokenCalls.get()).isEqualTo(1);
        assertThat(google.sendCalls.get()).isZero();

        // 2. set-gmail-token.sh wrote a new refresh token; the backend container is recreated: a new process.
        backendWith(NEW_TOKEN).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            JavaMailSender sender = ctx.getBean(JavaMailSender.class);
            assertThat(sender).isNotSameAs(oldSender.get());
            assertThat(tokensOf(sender)).isNotSameAs(oldTokens.get());
            assertThat(tokensOf(sender).authorizationLost()).as("no lost state carried over").isFalse();
            assertThat(google.tokenCalls.get()).as("nothing is called at startup").isEqualTo(1);

            // 3. Both kinds of email are sent again.
            ctx.getBean(SupplierMailer.class).passwordChanged(user);
            assertThat(google.sendCalls.get()).isEqualTo(1);
            assertThat(ctx.getBean(PasswordResetMailer.class)).isNotNull();
            sender.send(message());
            assertThat(google.sendCalls.get()).isEqualTo(2);
        });

        assertThat(google.tokenCalls.get()).as("one refused renewal, one new renewal").isEqualTo(2);
        assertThat(google.tokenForms.get(1)).contains("refresh_token=" + enc(NEW_TOKEN)).doesNotContain(enc(OLD_TOKEN));
        assertThat(google.authorizations).containsExactly("Bearer AT-2", "Bearer AT-2");
        MimeMessage sent = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(google.raws.get(0))));
        assertThat(sent.getFrom()[0].toString()).contains(GMAIL);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("supplier@example.com");

        // The old process's objects stay lost: they are never reused, and a new token does not revive them.
        assertThat(oldTokens.get().authorizationLost()).isTrue();
    }

    @Test
    void noMailClassKeepsMutableStaticState() {
        List<Class<?>> classes = List.of(GoogleAccessTokens.class, GmailApiMailSender.class, GoogleErrors.class,
                OutgoingMail.class, MailProviderConfig.class, MailProviderProperties.class,
                MailProviderProperties.Gmail.class, SenderAddresses.class, GmailApiSelected.class,
                MailTestCommandLine.class);
        for (Class<?> c : classes) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    assertThat(Modifier.isFinal(f.getModifiers())).as(c.getSimpleName() + "." + f.getName()).isTrue();
                }
            }
        }
    }
}
