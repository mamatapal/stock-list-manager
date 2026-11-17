package app.stocklist.mail;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the email transport (MAIL_PROVIDER).
 *
 * <ul>
 *   <li>smtp or unset: declares no sender. Spring Boot's mail auto-configuration creates its SMTP sender from the
 *       spring.mail settings exactly as before (or none when SMTP_HOST is empty).</li>
 *   <li>gmail-api: declares the only {@link JavaMailSender}, a {@link GmailApiMailSender}. Boot's auto-configuration
 *       creates a sender only when no MailSender bean exists, so it backs off even if SMTP_HOST is still set.</li>
 * </ul>
 * The Gmail settings and the From addresses are checked at startup without any network call; the first call to
 * Google happens when the first email is sent.
 */
@Configuration(proxyBeanMethods = false)
public class MailProviderConfig {
    private static final Logger log = LoggerFactory.getLogger(MailProviderConfig.class);

    @Bean
    @Conditional(GmailApiSelected.class)
    GmailApiMailSender gmailApiMailSender(MailProviderProperties props,
                                          @Value("${app.mail-from:}") String mailFrom,
                                          @Value("${app.supplier.mail-from:}") String supplierMailFrom) {
        MailProviderProperties.Gmail g = props.gmail();
        required(g.sender(), "GMAIL_SENDER");
        required(g.clientId(), "GMAIL_OAUTH_CLIENT_ID");
        required(g.clientSecret(), "GMAIL_OAUTH_CLIENT_SECRET");
        required(g.refreshToken(), "GMAIL_OAUTH_REFRESH_TOKEN");
        SenderAddresses.check(g.sender(), g.sendAsAliases(), mailFrom, supplierMailFrom);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        GoogleAccessTokens tokens = new GoogleAccessTokens(http, g.tokenUri(), g.clientId().trim(),
                g.clientSecret().trim(), g.refreshToken().trim(), Clock.systemUTC());
        return new GmailApiMailSender(http, g.apiBaseUri(), tokens);
    }

    @Bean
    OutgoingMail outgoingMail(MailProviderProperties props, ObjectProvider<JavaMailSender> sender,
                              @Value("${spring.mail.host:}") String smtpHost,
                              @Value("${app.mail-from:}") String mailFrom,
                              @Value("${app.supplier.mail-from:}") String supplierMailFrom) {
        OutgoingMail mail = new OutgoingMail(props.provider(), sender.getIfAvailable(), smtpHost, mailFrom,
                supplierMailFrom);
        if (props.provider() == MailProviderProperties.Provider.GMAIL_API && !mail.ready()) {
            log.warn("MAIL_PROVIDER=gmail-api but MAIL_FROM is empty: emails are not sent until it is set");
        }
        return mail;
    }

    private static void required(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(setting + " must be set when MAIL_PROVIDER=gmail-api");
        }
    }
}
