package app.stocklist.app.domain.catalogue

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Price-basis rules, identical to the server's StockListCalculator and checked against the same
 * calc-vectors.csv file:
 *  PER_UNIT  line total = quantity x unit price (HALF_UP, 2 places)
 *  TOTAL     line total = the amount entered - it is NEVER multiplied by the quantity again.
 */
object ListCalculator {
    const val PER_UNIT = "PER_UNIT"
    const val TOTAL = "TOTAL"

    fun lineTotal(basis: String, quantity: BigDecimal, enteredPrice: BigDecimal?): BigDecimal? {
        if (enteredPrice == null) return null
        return if (basis == TOTAL) enteredPrice.setScale(2, RoundingMode.HALF_UP)
        else quantity.multiply(enteredPrice).setScale(2, RoundingMode.HALF_UP)
    }

    fun effectiveUnitPrice(basis: String, quantity: BigDecimal, enteredPrice: BigDecimal?): BigDecimal? {
        if (enteredPrice == null) return null
        return if (basis == TOTAL) enteredPrice.divide(quantity, 4, RoundingMode.HALF_UP)
        else enteredPrice.setScale(4, RoundingMode.HALF_UP)
    }

    fun listTotal(lineTotals: List<BigDecimal?>): BigDecimal? =
        lineTotals.filterNotNull().takeIf { it.isNotEmpty() }?.reduce(BigDecimal::add)?.setScale(2, RoundingMode.HALF_UP)

    /** Max decimal places allowed for the entered price. */
    fun priceScale(basis: String): Int = if (basis == TOTAL) 2 else 4
}
