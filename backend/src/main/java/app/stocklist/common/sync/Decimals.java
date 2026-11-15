package app.stocklist.common.sync;

import app.stocklist.common.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money and quantities travel as strings ("12.50") and are parsed strictly into BigDecimal. Never double. */
public final class Decimals {
    private static final BigDecimal MAX = new BigDecimal("99999999999");

    private Decimals() {}

    public static BigDecimal parse(String value, String field, int maxScale, boolean allowZero) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (!v.matches("^[0-9]{1,11}(\\.[0-9]{1," + maxScale + "})?$")) {
            throw ApiException.badRequest("INVALID_NUMBER", field + " must be a number with at most " + maxScale
                    + " decimal places");
        }
        BigDecimal d = new BigDecimal(v);
        if (d.compareTo(MAX) > 0) {
            throw ApiException.badRequest("INVALID_NUMBER", field + " is too large");
        }
        if (!allowZero && d.signum() == 0) {
            throw ApiException.badRequest("INVALID_NUMBER", field + " must be more than 0");
        }
        return d;
    }

    public static String format(BigDecimal d) {
        return d == null ? null : d.stripTrailingZeros().toPlainString();
    }

    /** Money is always shown and stored with exactly 2 decimals. */
    public static String money(BigDecimal d) {
        return d == null ? null : d.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
