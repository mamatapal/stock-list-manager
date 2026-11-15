package app.stocklist.auth;

import app.stocklist.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses how reset codes reach the owner: by email when SMTP is configured (SMTP_HOST and friends), otherwise the
 * logging fallback, which only prints the code when MAIL_LOG_TOKENS=true (local development).
 */
@Configuration
public class MailConfig {
    @Bean
    PasswordResetMailer passwordResetMailer(ObjectProvider<JavaMailSender> sender, AppProperties props,
                                            @Value("${spring.mail.host:}") String host,
                                            @Value("${app.mail-from:}") String from) {
        JavaMailSender s = sender.getIfAvailable();
        if (s != null && !host.isBlank() && !from.isBlank()) {
            return new SmtpPasswordResetMailer(s, from, props.resetTokenTtl());
        }
        return new LoggingPasswordResetMailer(props);
    }
}
