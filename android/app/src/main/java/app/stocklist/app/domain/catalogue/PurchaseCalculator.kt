package app.stocklist.app.domain.catalogue

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Purchase money rules, identical to the server's PurchaseCalculator and checked against the same
 * purchase-calc-vectors.csv file. BigDecimal throughout; never floating point.
 *  Cost             PER_UNIT: quantity x purchase price.  TOTAL: the amount entered (never multiplied again).
 *  Unit price       PER_UNIT: the price entered.          TOTAL: amount / quantity, 4 places.
 *  Expected sales   quantity x selling price (selling price is always per unit).
 *  Expected profit  expected sales - cost.
 *  Margin %         profit / expected sales x 100 (N/A when sales is 0).
 *  Markup %         profit / cost x 100 (N/A when cost is 0).
 */
object PurchaseCalculator {
    const val PER_UNIT = "PER_UNIT"
    const val TOTAL = "TOTAL"
    private val HUNDRED = BigDecimal(100)

    data class Line(
        val cost: BigDecimal?, val unitPrice: BigDecimal?, val sales: BigDecimal?, val profit: BigDecimal?,
        val margin: BigDecimal?, val markup: BigDecimal?,
    )

    data class Totals(
        val cost: BigDecimal?, val sales: BigDecimal?, val profit: BigDecimal?, val pricedCost: BigDecimal?,
        val margin: BigDecimal?, val markup: BigDecimal?,
    )

    fun line(basis: String, quantity: BigDecimal, entered: BigDecimal?, selling: BigDecimal?): Line {
        var cost: BigDecimal? = null
        var unit: BigDecimal? = null
        if (entered != null) {
            if (basis == TOTAL) {
                cost = money(entered)
                unit = entered.divide(quantity, 4, RoundingMode.HALF_UP)
            } else {
                cost = money(quantity.multiply(entered))
                unit = entered.setScale(4, RoundingMode.HALF_UP)
            }
        }
        val sales = selling?.let { money(quantity.multiply(it)) }
        val profit = if (sales == null || cost == null) null else sales.subtract(cost)
        return Line(cost, unit, sales, profit, percent(profit, sales), percent(profit, cost))
    }

    fun totals(lines: List<Line>): Totals {
        val cost = sum(lines.map { it.cost })
        val sales = sum(lines.map { it.sales })
        val profit = sum(lines.map { it.profit })
        val priced = lines.filter { it.profit != null }
        val pricedCost = sum(priced.map { it.cost })
        val pricedSales = sum(priced.map { it.sales })
        return Totals(cost, sales, profit, pricedCost, percent(profit, pricedSales), percent(profit, pricedCost))
    }

    fun percent(part: BigDecimal?, whole: BigDecimal?): BigDecimal? {
        if (part == null || whole == null || whole.signum() == 0) return null
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP)
    }

    /** Max decimals for the purchase price: a total amount is money (2), a unit price may have 4. */
    fun priceScale(basis: String): Int = if (basis == TOTAL) 2 else 4

    private fun money(d: BigDecimal): BigDecimal = d.setScale(2, RoundingMode.HALF_UP)

    private fun sum(values: List<BigDecimal?>): BigDecimal? =
        values.filterNotNull().takeIf { it.isNotEmpty() }?.reduce(BigDecimal::add)?.let(::money)
}

/**
 * Partial deliveries, identical to the server's ReceivingCalculator (receiving-vectors.csv). The requested or
 * billed quantity never changes; remaining never goes below zero, an excess is "over by".
 */
object ReceivingCalculator {
    const val PENDING = "PENDING"
    const val PARTIAL = "PARTIAL"
    const val FULL = "FULL"

    data class Progress(
        val requested: BigDecimal, val received: BigDecimal, val remaining: BigDecimal, val overBy: BigDecimal,
        val status: String,
    )

    fun progress(requested: BigDecimal, received: BigDecimal?): Progress {
        val rec = received ?: BigDecimal.ZERO
        val diff = requested.subtract(rec)
        val status = when {
            rec.signum() <= 0 -> PENDING
            diff.signum() <= 0 -> FULL
            else -> PARTIAL
        }
        return Progress(requested, rec, if (diff.signum() > 0) diff else BigDecimal.ZERO,
            if (diff.signum() < 0) diff.negate() else BigDecimal.ZERO, status)
    }

    fun overall(statuses: List<String>): String = when {
        statuses.isEmpty() || statuses.all { it == PENDING } -> PENDING
        statuses.all { it == FULL } -> FULL
        else -> PARTIAL
    }

    fun label(status: String): String = when (status) {
        FULL -> "Fully received"
        PARTIAL -> "Partially received"
        else -> "Pending"
    }
}
