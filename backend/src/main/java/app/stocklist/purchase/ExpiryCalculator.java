package app.stocklist.purchase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Expiry rules, shared with the Android app (both run expiry-vectors.csv).
 *  days left = expiry date - today (calendar days; 0 = expires today, negative = already expired).
 *  EXPIRED   days left < 0.
 *  WITHIN_7  0 <= days left <= 7   (likewise 30 and 90; the windows include today and overlap: an item due in
 *            5 days is in the 7-, 30- and 90-day lists).
 *  ALL       every batch that has an expiry date, expired or not.
 * Batches without an expiry date are never in an expiry list. Batches with nothing left (all corrected away) and
 * batches of cancelled purchases are left out by the queries, not by these rules.
 */
public final class ExpiryCalculator {
    public enum Filter { EXPIRED, WITHIN_7, WITHIN_30, WITHIN_90, ALL }

    private ExpiryCalculator() {}

    public static Long daysLeft(LocalDate expiry, LocalDate today) {
        return expiry == null ? null : ChronoUnit.DAYS.between(today, expiry);
    }

    public static boolean matches(Filter filter, LocalDate expiry, LocalDate today) {
        if (expiry == null) return false;
        long d = daysLeft(expiry, today);
        return switch (filter) {
            case EXPIRED -> d < 0;
            case WITHIN_7 -> d >= 0 && d <= 7;
            case WITHIN_30 -> d >= 0 && d <= 30;
            case WITHIN_90 -> d >= 0 && d <= 90;
            case ALL -> true;
        };
    }

    /** The date range a filter covers, for database queries: [from, to], either end open (null). */
    public static LocalDate[] range(Filter filter, LocalDate today) {
        return switch (filter) {
            case EXPIRED -> new LocalDate[] {null, today.minusDays(1)};
            case WITHIN_7 -> new LocalDate[] {today, today.plusDays(7)};
            case WITHIN_30 -> new LocalDate[] {today, today.plusDays(30)};
            case WITHIN_90 -> new LocalDate[] {today, today.plusDays(90)};
            case ALL -> new LocalDate[] {null, null};
        };
    }

    /** Short label for lists and alerts. */
    public static String label(LocalDate expiry, LocalDate today) {
        Long d = daysLeft(expiry, today);
        if (d == null) return "No expiry date";
        if (d < 0) return "Expired " + (-d) + (d == -1 ? " day ago" : " days ago");
        if (d == 0) return "Expires today";
        return "Expires in " + d + (d == 1 ? " day" : " days");
    }
}
