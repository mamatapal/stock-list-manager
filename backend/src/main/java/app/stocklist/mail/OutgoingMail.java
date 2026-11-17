package app.stocklist.mail;

import org.springframework.mail.javamail.JavaMailSender;

/**
 * The one decision both mail configurations use (auth/MailConfig for the shop owner's password reset,
 * supplieraccount/SupplierMailConfig for supplier emails): whether email can be sent, through which sender, and from
 * which address. When it is not ready, both keep their logging stand-ins, as before.
 *
 * <ul>
 *   <li>smtp (default): ready when a JavaMailSender exists, SMTP_HOST is set and MAIL_FROM is set (the rule both
 *       configurations used before).</li>
 *   <li>gmail-api: ready when the Gmail API sender exists and MAIL_FROM is set.</li>
 * </ul>
 * Supplier emails use SUPPLIER_MAIL_FROM when it is set, otherwise MAIL_FROM (as before).
 */
public final class OutgoingMail {
    private final MailProviderProperties.Provider provider;
    private final JavaMailSender sender;
    private final String from;
    private final String supplierFrom;
    private final boolean ready;

    public OutgoingMail(MailProviderProperties.Provider provider, JavaMailSender sender, String smtpHost,
                        String mailFrom, String supplierMailFrom) {
        this.provider = provider;
        this.sender = sender;
        this.from = blank(mailFrom) ? "" : mailFrom;
        this.supplierFrom = blank(supplierMailFrom) ? this.from : supplierMailFrom;
        boolean transport = provider == MailProviderProperties.Provider.GMAIL_API
                ? sender instanceof GmailApiMailSender
                : sender != null && !blank(smtpHost);
        this.ready = transport && !blank(mailFrom);
    }

    public boolean ready() {
        return ready;
    }

    public JavaMailSender sender() {
        return sender;
    }

    public String from() {
        return from;
    }

    public String supplierFrom() {
        return supplierFrom;
    }

    public MailProviderProperties.Provider provider() {
        return provider;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
