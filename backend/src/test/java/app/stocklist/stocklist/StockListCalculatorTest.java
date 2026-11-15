package app.stocklist.stocklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.stocklist.common.sync.Decimals;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockListCalculatorTest {

    @Test
    void sharedVectors() throws Exception {
        int checked = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/calc-vectors.csv"), StandardCharsets.UTF_8))) {
            String row;
            while ((row = r.readLine()) != null) {
                if (row.isBlank() || row.startsWith("#")) continue;
                String[] c = Arrays.copyOf(row.split(",", -1), 5);
                BigDecimal qty = new BigDecimal(c[1]);
                BigDecimal price = c[2].isEmpty() ? null : new BigDecimal(c[2]);
                BigDecimal total = StockListCalculator.lineTotal(c[0], qty, price);
                BigDecimal unit = StockListCalculator.effectiveUnitPrice(c[0], qty, price);
                if (c[3].isEmpty()) {
                    assertNull(total, row);
                } else {
                    assertEquals(c[3], Decimals.money(total), row);
                    assertEquals(c[4], Decimals.format(unit), row);
                }
                checked++;
            }
        }
        assertEquals(13, checked);
    }

    @Test
    void listTotalSkipsLinesWithoutPrice() {
        assertEquals("100.10", Decimals.money(StockListCalculator.listTotal(
                Arrays.asList(new BigDecimal("100.00"), null, new BigDecimal("0.10")))));
        assertNull(StockListCalculator.listTotal(List.of()));
    }

    @Test
    void decimalsAreParsedStrictly() {
        assertEquals(new BigDecimal("2.75"), Decimals.parse("2.75", "q", 3, false));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> Decimals.parse("2.7500", "q", 3, false));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> Decimals.parse("1e3", "q", 3, false));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> Decimals.parse("0", "q", 3, false));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> Decimals.parse("-1", "q", 3, true));
    }
}
