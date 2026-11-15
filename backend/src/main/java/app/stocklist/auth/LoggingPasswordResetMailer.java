package app.stocklist.auth;

import app.stocklist.config.AppProperties;
import app.stocklist.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no SMTP server is configured (see {@link MailConfig}). Never logs the token unless MAIL_LOG_TOKENS=true,
 * which is only for local development.
 */
public class LoggingPasswordResetMailer implements PasswordResetMailer {
    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);
    private final boolean logTokens;

    public LoggingPasswordResetMailer(AppProperties props) {
        this.logTokens = props.mailLogTokens();
    }

    @Override
    public void send(AppUser user, String rawToken) {
        if (logTokens) {
            log.warn("DEV ONLY - password reset token for user {}: {}", user.getId(), rawToken);
        } else {
            log.info("Password reset requested for user {}; no mail sender configured", user.getId());
        }
    }
}
