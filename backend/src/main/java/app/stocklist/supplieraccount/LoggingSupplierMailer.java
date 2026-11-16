package app.stocklist.supplieraccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used until an email service is configured (development, or production before the SES domain exists). Nothing is
 * sent. Links are printed only when MAIL_LOG_TOKENS=true, which is for local development only.
 */
public class LoggingSupplierMailer implements SupplierMailer {
    private static final Logger log = LoggerFactory.getLogger(LoggingSupplierMailer.class);
    private final boolean logLinks;

    public LoggingSupplierMailer(boolean logLinks) {
        this.logLinks = logLinks;
    }

    @Override
    public void verificationLink(SupplierUser user, String link) {
        write("verification link", user, link);
    }

    @Override
    public void passwordResetLink(SupplierUser user, String link) {
        write("password reset link", user, link);
    }

    @Override
    public void alreadyRegistered(SupplierUser user, String resetLink) {
        write("already-registered notice", user, resetLink);
    }

    @Override
    public void passwordChanged(SupplierUser user) {
        log.info("Supplier password-changed notice for user {} not sent: no mail server configured", user.getId());
    }

    @Override
    public void statusChanged(SupplierUser user, SupplierOrg org) {
        log.info("Supplier status email ({}) for user {} not sent: no mail server configured", org.getStatus(),
                user.getId());
    }

    private void write(String kind, SupplierUser user, String link) {
        if (logLinks) {
            log.warn("DEV ONLY - supplier {} for user {}: {}", kind, user.getId(), link);
        } else {
            log.info("Supplier {} for user {} not sent: no mail server configured", kind, user.getId());
        }
    }
}
