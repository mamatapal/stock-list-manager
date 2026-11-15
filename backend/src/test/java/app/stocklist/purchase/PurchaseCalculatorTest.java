package app.stocklist.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The shared tables; the Android app runs the same files. */
class PurchaseCalculatorTest {

    private static List<String[]> rows(String file) throws IOException {
        try (InputStream in = PurchaseCalculatorTest.class.getResourceAsStream("/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#")).map(l -> l.split(",", -1)).toList();
        }
    }

    private static BigDecimal num(String s) {
        return s.isEmpty() ? null : new BigDecimal(s);
    }

    private static String str(BigDecimal d) {
        return d == null ? "" : d.toPlainString();
    }

    @Test
    void purchaseVectors() throws IOException {
        List<String[]> rows = rows("purchase-calc-vectors.csv");
        assertTrue(rows.size() >= 10);
        for (String[] c : rows) {
            var l = PurchaseCalculator.line(c[0], new BigDecimal(c[1]), num(c[2]), num(c[3]));
            String row = String.join(",", c);
            assertEquals(c[4], str(l.cost()), "cost " + row);
            assertEquals(c[5], str(l.unitPrice()), "unit price " + row);
            assertEquals(c[6], str(l.sales()), "sales " + row);
            assertEquals(c[7], str(l.profit()), "profit " + row);
            assertEquals(c[8], str(l.margin()), "margin " + row);
            assertEquals(c[9], str(l.markup()), "markup " + row);
        }
    }

    @Test
    void totalsAreSumsOfRoundedLines() {
        var a = PurchaseCalculator.line("PER_UNIT", new BigDecimal("20"), new BigDecimal("140"), new BigDecimal("165"));
        var b = PurchaseCalculator.line("TOTAL", new BigDecimal("1.5"), new BigDecimal("270"), null);
        var c = PurchaseCalculator.line("PER_UNIT", new BigDecimal("10"), new BigDecimal("5"), BigDecimal.ZERO);
        var t = PurchaseCalculator.totals(List.of(a, b, c));
        assertEquals("3120.00", t.cost().toPlainString());
        assertEquals("3300.00", t.sales().toPlainString());
        assertEquals("450.00", t.profit().toPlainString());
        assertEquals("2850.00", t.pricedCost().toPlainString());
        assertEquals("13.64", t.margin().toPlainString());
        assertEquals("15.79", t.markup().toPlainString());
        var none = PurchaseCalculator.totals(List.of(PurchaseCalculator.line("PER_UNIT", BigDecimal.ONE, null, null)));
        assertNull(none.cost());
        assertNull(none.margin());
    }

    @Test
    void receivingVectors() throws IOException {
        for (String[] c : rows("receiving-vectors.csv")) {
            var p = ReceivingCalculator.progress(new BigDecimal(c[0]), new BigDecimal(c[1]));
            String row = String.join(",", c);
            assertEquals(0, new BigDecimal(c[2]).compareTo(p.remaining()), "remaining " + row);
            assertEquals(0, new BigDecimal(c[3]).compareTo(p.overBy()), "over by " + row);
            assertEquals(c[4], p.status(), "status " + row);
        }
        assertEquals("PENDING", ReceivingCalculator.overall(List.of("PENDING", "PENDING")));
        assertEquals("PARTIAL", ReceivingCalculator.overall(List.of("FULL", "PENDING")));
        assertEquals("FULL", ReceivingCalculator.overall(List.of("FULL", "FULL")));
    }
}
