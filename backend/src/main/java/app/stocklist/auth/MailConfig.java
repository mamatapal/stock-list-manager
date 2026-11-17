package app.stocklist.auth;

import app.stocklist.config.AppProperties;
import app.stocklist.mail.OutgoingMail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses how reset codes reach the owner: by email when outgoing email is ready (SMTP or the Gmail API, see
 * {@link OutgoingMail}), otherwise the logging fallback, which only prints the code when MAIL_LOG_TOKENS=true (local
 * development).
 */
@Configuration
public class MailConfig {
    @Bean
    PasswordResetMailer passwordResetMailer(OutgoingMail mail, AppProperties props) {
        if (mail.ready()) {
            return new SmtpPasswordResetMailer(mail.sender(), mail.from(), props.resetTokenTtl());
        }
        return new LoggingPasswordResetMailer(props);
    }
}
