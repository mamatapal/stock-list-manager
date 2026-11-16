package app.stocklist.app.domain.catalogue

import app.stocklist.app.core.i18n.asciiDigits
import app.stocklist.app.core.i18n.tr

import java.math.BigDecimal
import java.math.RoundingMode

/** Money and quantities as BigDecimal from strings. Same rules as the server's Decimals.java. */
object Decimals {
    private val MAX = BigDecimal("99999999999")

    /** Returns null when blank; throws IllegalArgumentException with a user-facing message when invalid. */
    fun parse(value: String?, label: String, maxScale: Int, allowZero: Boolean): BigDecimal? {
        val v = asciiDigits(value?.trim().orEmpty())
        if (v.isEmpty()) return null
        if (!Regex("^[0-9]{1,11}(\\.[0-9]{1,$maxScale})?$").matches(v)) {
            throw IllegalArgumentException(tr("%1\$s: enter a number with at most %2\$s decimal places", label, maxScale))
        }
        val d = BigDecimal(v)
        if (d > MAX) throw IllegalArgumentException(tr("%1\$s is too large", label))
        if (!allowZero && d.signum() == 0) throw IllegalArgumentException(tr("%1\$s must be more than 0", label))
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
