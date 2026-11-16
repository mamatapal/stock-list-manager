package app.stocklist.supplieraccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends supplier emails through the configured SMTP server (Amazon SES in production). Links and tokens are never
 * logged; a failure is logged without them and the API answers the same either way.
 */
public class SmtpSupplierMailer implements SupplierMailer {
    private static final Logger log = LoggerFactory.getLogger(SmtpSupplierMailer.class);
    private final JavaMailSender sender;
    private final String from;
    private final SupplierProperties props;

    public SmtpSupplierMailer(JavaMailSender sender, String from, SupplierProperties props) {
        this.sender = sender;
        this.from = from;
        this.props = props;
    }

    @Override
    public void verificationLink(SupplierUser user, String link) {
        send(user, "verification", SupplierEmails.verification(user, link, props.verifyLinkTtl()));
    }

    @Override
    public void passwordResetLink(SupplierUser user, String link) {
        send(user, "password reset", SupplierEmails.passwordReset(user, link, props.resetLinkTtl()));
    }

    @Override
    public void alreadyRegistered(SupplierUser user, String resetLink) {
        send(user, "already registered", SupplierEmails.alreadyRegistered(user, resetLink));
    }

    @Override
    public void statusChanged(SupplierUser user, SupplierOrg org) {
        send(user, "status " + org.getStatus(), SupplierEmails.statusChanged(user, org));
    }

    private void send(SupplierUser user, String kind, SupplierEmails.Email email) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(user.getEmail());
        m.setSubject(email.subject());
        m.setText(email.body());
        try {
            sender.send(m);
            log.info("Supplier {} email sent to user {}", kind, user.getId());
        } catch (MailException e) {
            log.error("Supplier {} email to user {} could not be sent: {}", kind, user.getId(),
                    e.getClass().getSimpleName());
        }
    }
}
