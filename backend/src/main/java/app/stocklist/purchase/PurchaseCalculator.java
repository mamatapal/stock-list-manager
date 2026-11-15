package app.stocklist.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Purchase money rules. The Android app has an identical copy; both run purchase-calc-vectors.csv.
 *
 *  Cost             PER_UNIT: quantity x purchase price.  TOTAL: the amount entered (never multiplied again).
 *  Unit price       PER_UNIT: the price entered.          TOTAL: amount / quantity, 4 places.
 *  Expected sales   quantity x selling price (the selling price is always per unit).
 *  Expected profit  expected sales - cost (both already rounded).
 *  Margin %         profit / expected sales x 100;  N/A (null) when expected sales is 0.
 *  Markup %         profit / cost x 100;            N/A (null) when cost is 0.
 * Money and percentages round HALF_UP to 2 places line by line; purchase totals are sums of rounded lines.
 * A missing price gives null (not zero) for everything that depends on it.
 */
public final class PurchaseCalculator {
    public static final String PER_UNIT = "PER_UNIT";
    public static final String TOTAL = "TOTAL";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PurchaseCalculator() {}

    public record Line(BigDecimal cost, BigDecimal unitPrice, BigDecimal sales, BigDecimal profit,
                       BigDecimal margin, BigDecimal markup) {}

    public record Totals(BigDecimal cost, BigDecimal sales, BigDecimal profit, BigDecimal pricedCost,
                         BigDecimal margin, BigDecimal markup) {}

    public static Line line(String basis, BigDecimal quantity, BigDecimal entered, BigDecimal selling) {
        BigDecimal cost = null;
        BigDecimal unit = null;
        if (entered != null) {
            if (TOTAL.equals(basis)) {
                cost = money(entered);
                unit = entered.divide(quantity, 4, RoundingMode.HALF_UP);
            } else {
                cost = money(quantity.multiply(entered));
                unit = entered.setScale(4, RoundingMode.HALF_UP);
            }
        }
        BigDecimal sales = selling == null ? null : money(quantity.multiply(selling));
        BigDecimal profit = sales == null || cost == null ? null : sales.subtract(cost);
        return new Line(cost, unit, sales, profit, percent(profit, sales), percent(profit, cost));
    }

    public static Totals totals(List<Line> lines) {
        BigDecimal cost = sum(lines.stream().map(Line::cost).toList());
        BigDecimal sales = sum(lines.stream().map(Line::sales).toList());
        BigDecimal profit = sum(lines.stream().map(Line::profit).toList());
        // Margin and markup compare like with like: only lines that have both a cost and a selling price.
        BigDecimal pricedCost = sum(lines.stream().filter(l -> l.profit() != null).map(Line::cost).toList());
        BigDecimal pricedSales = sum(lines.stream().filter(l -> l.profit() != null).map(Line::sales).toList());
        return new Totals(cost, sales, profit, pricedCost, percent(profit, pricedSales), percent(profit, pricedCost));
    }

    /** part / whole x 100, 2 places; null when either is missing or whole is 0. */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) return null;
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal d) {
        return d.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = null;
        for (BigDecimal v : values) {
            if (v != null) total = total == null ? v : total.add(v);
        }
        return total == null ? null : money(total);
    }
}
