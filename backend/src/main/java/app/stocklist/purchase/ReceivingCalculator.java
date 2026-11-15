package app.stocklist.purchase;

import java.math.BigDecimal;
import java.util.List;

/**
 * Partial deliveries. The requested (or billed) quantity never changes; received is the sum of every delivery
 * row (receipts and corrections). Remaining never goes below zero: an excess is reported as "over by".
 * The Android app has an identical copy; both run receiving-vectors.csv.
 */
public final class ReceivingCalculator {
    public static final String PENDING = "PENDING";
    public static final String PARTIAL = "PARTIAL";
    public static final String FULL = "FULL";

    private ReceivingCalculator() {}

    public record Progress(BigDecimal requested, BigDecimal received, BigDecimal remaining, BigDecimal overBy,
                           String status) {}

    public static Progress progress(BigDecimal requested, BigDecimal received) {
        BigDecimal rec = received == null ? BigDecimal.ZERO : received;
        BigDecimal diff = requested.subtract(rec);
        String status = rec.signum() <= 0 ? PENDING : diff.signum() <= 0 ? FULL : PARTIAL;
        return new Progress(requested, rec, diff.signum() > 0 ? diff : BigDecimal.ZERO,
                diff.signum() < 0 ? diff.negate() : BigDecimal.ZERO, status);
    }

    /** A whole list or purchase: PENDING when nothing arrived, FULL when every line is full, else PARTIAL. */
    public static String overall(List<String> lineStatuses) {
        if (lineStatuses.isEmpty() || lineStatuses.stream().allMatch(PENDING::equals)) return PENDING;
        if (lineStatuses.stream().allMatch(FULL::equals)) return FULL;
        return PARTIAL;
    }
}
