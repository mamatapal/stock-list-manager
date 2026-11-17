package app.stocklist.mail;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Startup check for the Gmail API: every From address must be the Gmail account itself (GMAIL_SENDER) or one of its
 * "Send mail as" aliases listed in GMAIL_SEND_AS_ALIASES. Gmail would otherwise silently replace the From address.
 * Errors name the setting, never its value. No network call.
 */
final class SenderAddresses {
    private SenderAddresses() {}

    static void check(String gmailSender, String sendAsAliases, String mailFrom, String supplierMailFrom) {
        Set<String> allowed = new HashSet<>();
        allowed.add(key(address(gmailSender, "GMAIL_SENDER")));
        if (sendAsAliases != null) {
            for (String alias : sendAsAliases.split(",")) {
                if (!alias.isBlank()) allowed.add(key(address(alias, "GMAIL_SEND_AS_ALIASES")));
            }
        }
        mustBeAllowed(mailFrom, "MAIL_FROM", allowed);
        mustBeAllowed(supplierMailFrom, "SUPPLIER_MAIL_FROM", allowed);
    }

    private static void mustBeAllowed(String value, String setting, Set<String> allowed) {
        if (value == null || value.isBlank()) return;
        if (!allowed.contains(key(address(value, setting)))) {
            throw new IllegalStateException(setting + " must use the GMAIL_SENDER address or an address listed in"
                    + " GMAIL_SEND_AS_ALIASES when MAIL_PROVIDER=gmail-api");
        }
    }

    /** The bare address of one mailbox, with or without a display name ("Shop <a@b.in>" gives a@b.in). */
    static String address(String value, String setting) {
        if (value == null || value.isBlank()) throw new IllegalStateException(setting + " is empty");
        try {
            InternetAddress[] list = InternetAddress.parse(value.trim(), true);
            if (list.length != 1) throw new IllegalStateException(setting + " must contain exactly one email address");
            list[0].validate();
            String address = list[0].getAddress();
            if (address == null || address.indexOf('@') <= 0) {
                throw new IllegalStateException(setting + " is not a valid email address");
            }
            return address;
        } catch (AddressException e) {
            throw new IllegalStateException(setting + " is not a valid email address");
        }
    }

    private static String key(String address) {
        return address.toLowerCase(Locale.ROOT);
    }
}
