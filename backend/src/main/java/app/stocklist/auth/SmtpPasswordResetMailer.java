package app.stocklist.auth;

import app.stocklist.user.AppUser;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Emails the owner their one-time reset code. The code is never logged. A failure to send is logged without the
 * code; the API answers the same either way, so it cannot be used to find out which usernames exist.
 */
public class SmtpPasswordResetMailer implements PasswordResetMailer {
    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);
    private final JavaMailSender sender;
    private final String from;
    private final Duration validFor;

    public SmtpPasswordResetMailer(JavaMailSender sender, String from, Duration validFor) {
        this.sender = sender;
        this.from = from;
        this.validFor = validFor;
    }

    @Override
    public void send(AppUser user, String rawToken) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(user.getEmail());
        m.setSubject("Your Stock List Manager password reset code");
        m.setText(body(user, rawToken, validFor));
        try {
            sender.send(m);
            log.info("Password reset email sent for user {}", user.getId());
        } catch (MailException e) {
            log.error("Password reset email for user {} could not be sent: {}", user.getId(), e.getClass().getSimpleName());
        }
    }

    static String body(AppUser user, String code, Duration validFor) {
        return """
                Hello %s,

                Someone asked to reset the password of the Stock List Manager owner account "%s".

                Your reset code is:

                %s

                Open the app, choose "Forgot password", and enter this code with your new password.
                The code works once and expires in %d minutes.

                If you did not ask for this, you can ignore this email; your password has not changed.
                """.formatted(user.getFullName(), user.getUsername(), code, validFor.toMinutes());
    }
}
