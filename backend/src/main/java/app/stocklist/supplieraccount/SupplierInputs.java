package app.stocklist.supplieraccount;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks and cleans supplier input. Every failure is a {@link SupplierPortalError} the app can translate. */
public final class SupplierInputs {
    private static final Pattern MOBILE = Pattern.compile("[6-9][0-9]{9}");
    private static final Pattern PIN = Pattern.compile("[1-9][0-9]{5}");
    private static final Pattern GSTIN = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}");
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890", "qwerty123",
            "qwertyuiop", "11111111", "00000000", "iloveyou", "admin123", "welcome1", "abc12345",
            "letmein1", "india123", "passw0rd", "87654321", "asdfghjk", "zxcvbnm1", "supplier1", "supplier123");

    private SupplierInputs() {}

    public static SupplierDtos.BusinessDetails details(String legalName, String tradeName, String contactPerson,
                                                       String mobile, String address, String city, String state,
                                                       String pinCode, String gstin) {
        String legal = trim(legalName);
        if (legal == null) throw SupplierPortalError.NAME_REQUIRED.ex();
        String contact = trim(contactPerson);
        if (contact == null) throw SupplierPortalError.CONTACT_REQUIRED.ex();
        String addr = trim(address), c = trim(city), s = trim(state);
        if (addr == null || c == null || s == null) throw SupplierPortalError.ADDRESS_REQUIRED.ex();
        String trade = trim(tradeName);
        max(legal, 150);
        max(trade, 150);
        max(contact, 120);
        max(addr, 300);
        max(c, 80);
        max(s, 80);
        return new SupplierDtos.BusinessDetails(legal, trade, contact, mobile(mobile), addr, c, s, pinCode(pinCode),
                gstin(gstin));
    }

    /** 10 digits starting 6-9; spaces, dashes and a leading +91 or 0 are removed first. */
    public static String mobile(String raw) {
        String v = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (v.startsWith("+91")) v = v.substring(3);
        else if (v.length() == 11 && v.startsWith("0")) v = v.substring(1);
        if (!MOBILE.matcher(v).matches()) throw SupplierPortalError.INVALID_MOBILE.ex();
        return v;
    }

    public static String email(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (v.length() > 254 || !EMAIL.matcher(v).matches()) throw SupplierPortalError.INVALID_EMAIL.ex();
        return v;
    }

    /** For look-ups (sign in, resend, forgot password): never fails, so it reveals nothing about the address. */
    public static String emailKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    static String pinCode(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!PIN.matcher(v).matches()) throw SupplierPortalError.INVALID_PIN_CODE.ex();
        return v;
    }

    static String gstin(String raw) {
        String v = trim(raw);
        if (v == null) return null;
        v = v.toUpperCase(Locale.ROOT);
        if (!GSTIN.matcher(v).matches()) throw SupplierPortalError.INVALID_GSTIN.ex();
        return v;
    }

    public static void password(String password, String email) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw SupplierPortalError.WEAK_PASSWORD.ex();
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower) || (email != null && lower.equals(email))) {
            throw SupplierPortalError.WEAK_PASSWORD.ex();
        }
    }

    static String trim(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static void max(String s, int max) {
        if (s != null && s.length() > max) throw SupplierPortalError.TEXT_TOO_LONG.ex();
    }
}
