package app.stocklist.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Date calculations and expiry filters; the Android app runs the same table. */
class ExpiryCalculatorTest {
    private static final ExpiryCalculator.Filter[] FILTERS = {ExpiryCalculator.Filter.EXPIRED,
            ExpiryCalculator.Filter.WITHIN_7, ExpiryCalculator.Filter.WITHIN_30, ExpiryCalculator.Filter.WITHIN_90,
            ExpiryCalculator.Filter.ALL};

    private static List<String[]> rows() throws IOException {
        try (InputStream in = ExpiryCalculatorTest.class.getResourceAsStream("/expiry-vectors.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#")).map(l -> l.split(",", -1)).toList();
        }
    }

    @Test
    void sharedVectors() throws IOException {
        List<String[]> rows = rows();
        assertTrue(rows.size() >= 12);
        for (String[] c : rows) {
            LocalDate today = LocalDate.parse(c[0]);
            LocalDate expiry = c[1].isEmpty() ? null : LocalDate.parse(c[1]);
            String row = String.join(",", c);
            Long days = ExpiryCalculator.daysLeft(expiry, today);
            assertEquals(c[2], days == null ? "" : days.toString(), "days " + row);
            for (int i = 0; i < FILTERS.length; i++) {
                assertEquals(Boolean.parseBoolean(c[3 + i]), ExpiryCalculator.matches(FILTERS[i], expiry, today),
                        FILTERS[i] + " " + row);
            }
            assertEquals(c[8], ExpiryCalculator.label(expiry, today), "label " + row);
        }
    }

    /** The database ranges used by the queries select exactly what matches() accepts, for 400 days around today. */
    @Test
    void queryRangesAgreeWithTheRules() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        for (ExpiryCalculator.Filter f : FILTERS) {
            LocalDate[] r = ExpiryCalculator.range(f, today);
            for (int d = -200; d <= 200; d++) {
                LocalDate e = today.plusDays(d);
                boolean inRange = (r[0] == null || !e.isBefore(r[0])) && (r[1] == null || !e.isAfter(r[1]));
                assertEquals(ExpiryCalculator.matches(f, e, today), inRange, f + " at " + d + " days");
            }
        }
        assertNull(ExpiryCalculator.daysLeft(null, today));
    }
}
