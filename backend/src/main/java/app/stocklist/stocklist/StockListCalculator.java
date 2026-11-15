package app.stocklist.stocklist;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Price-basis rules (identical copy in the Android app, both checked against calc-vectors.json):
 * PER_UNIT  line total = quantity x unit price, rounded HALF_UP to 2 places.
 * TOTAL     line total = the amount entered. It is NEVER multiplied by the quantity.
 * A line without a price has no total; the list total is the sum of the lines that have one.
 */
public final class StockListCalculator {
    public static final String PER_UNIT = "PER_UNIT";
    public static final String TOTAL = "TOTAL";

    private StockListCalculator() {}

    public static BigDecimal lineTotal(String basis, BigDecimal quantity, BigDecimal enteredPrice) {
        if (enteredPrice == null) {
            return null;
        }
        if (TOTAL.equals(basis)) {
            return enteredPrice.setScale(2, RoundingMode.HALF_UP);
        }
        return quantity.multiply(enteredPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /** Unit price to show for information; for TOTAL lines it is the total divided by the quantity. */
    public static BigDecimal effectiveUnitPrice(String basis, BigDecimal quantity, BigDecimal enteredPrice) {
        if (enteredPrice == null) {
            return null;
        }
        if (TOTAL.equals(basis)) {
            return enteredPrice.divide(quantity, 4, RoundingMode.HALF_UP);
        }
        return enteredPrice.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal listTotal(Collection<BigDecimal> lineTotals) {
        return lineTotals.stream().filter(Objects::nonNull).reduce(BigDecimal::add)
                .map(t -> t.setScale(2, RoundingMode.HALF_UP)).orElse(null);
    }
}
