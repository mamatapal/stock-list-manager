package app.stocklist.app.domain.catalogue

import java.math.BigDecimal
import java.math.RoundingMode

/** Money and quantities as BigDecimal from strings. Same rules as the server's Decimals.java. */
object Decimals {
    private val MAX = BigDecimal("99999999999")

    /** Returns null when blank; throws IllegalArgumentException with a user-facing message when invalid. */
    fun parse(value: String?, label: String, maxScale: Int, allowZero: Boolean): BigDecimal? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (!Regex("^[0-9]{1,11}(\\.[0-9]{1,$maxScale})?$").matches(v)) {
            throw IllegalArgumentException("$label: enter a number with at most $maxScale decimal places")
        }
        val d = BigDecimal(v)
        if (d > MAX) throw IllegalArgumentException("$label is too large")
        if (!allowZero && d.signum() == 0) throw IllegalArgumentException("$label must be more than 0")
        return d
    }

    fun tryParse(value: String?, maxScale: Int, allowZero: Boolean): BigDecimal? =
        try { parse(value, "", maxScale, allowZero) } catch (e: IllegalArgumentException) { null }

    fun format(d: BigDecimal?): String? = d?.stripTrailingZeros()?.toPlainString()

    fun money(d: BigDecimal?): String? = d?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()

    /** "₹1,23,456.50" with Indian digit grouping. */
    fun rupees(amount: String?): String {
        val d = amount?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "-"
        val fmt = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.Builder().setLanguage("en").setRegion("IN").build())
        fmt.minimumFractionDigits = 2
        fmt.maximumFractionDigits = 2
        return fmt.format(d)
    }
}
