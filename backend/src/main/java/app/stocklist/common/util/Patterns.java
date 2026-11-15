package app.stocklist.common.util;

/** Shared input formats (Indian mobile numbers, PIN codes, GSTIN). */
public final class Patterns {
    private Patterns() {}

    public static final String USERNAME = "^[A-Za-z0-9._]{3,40}$";
    public static final String MOBILE = "^[6-9][0-9]{9}$";
    public static final String PIN_CODE = "^[1-9][0-9]{5}$";
    public static final String GSTIN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    /** Indian mobile, or a landline with STD code (6-12 digits, optional leading 0 or +91). */
    public static final String MOBILE_OR_PHONE = "(\\+91)?[0-9]{6,12}";
    public static final String DEVICE_ID = "^[A-Za-z0-9-]{8,64}$";
}
