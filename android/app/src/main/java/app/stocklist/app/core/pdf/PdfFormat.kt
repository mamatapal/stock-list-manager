package app.stocklist.app.core.pdf

import app.stocklist.app.core.i18n.tr

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Text formatting for PDFs and reports. Pure Kotlin (no locale lookups), so the output is the same on every phone:
 * ₹ with Indian digit grouping (₹1,23,456.50), dates as DD-MM-YYYY, quantities without trailing zeros (2.5, 0.125).
 */
object PdfFormat {
    const val RUPEE = "₹"
    const val NONE = "-"
    private val DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

    private fun parse(v: String?): BigDecimal? = v?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    /** ₹ amount, exactly 2 decimals. */
    fun money(v: String?): String = parse(v)?.let { money(it) } ?: NONE

    fun money(d: BigDecimal): String {
        val s = d.setScale(2, RoundingMode.HALF_UP)
        return (if (s.signum() < 0) "-" else "") + RUPEE + grouped(s.abs().toPlainString())
    }

    /** ₹ unit price: at least 2 and at most 4 decimals (₹12.50, ₹0.3333). */
    fun unitPrice(v: String?): String {
        val d = parse(v) ?: return NONE
        val stripped = d.stripTrailingZeros()
        val scale = stripped.scale().coerceIn(2, 4)
        val s = d.setScale(scale, RoundingMode.HALF_UP)
        return (if (s.signum() < 0) "-" else "") + RUPEE + grouped(s.abs().toPlainString())
    }

    /** Quantity without trailing zeros, grouped: 1,250.5 */
    fun qty(v: String?): String {
        val d = parse(v) ?: return NONE
        val s = d.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        return (if (s.signum() < 0) "-" else "") + grouped(s.abs().toPlainString())
    }

    fun qty(v: String?, unit: String?): String = qty(v).let { if (it == NONE || unit.isNullOrBlank()) it else "$it $unit" }

    fun percent(v: String?): String = parse(v)?.let { it.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%" } ?: NONE

    /** yyyy-MM-dd (or a full ISO instant) as DD-MM-YYYY. */
    fun date(v: String?): String {
        if (v.isNullOrBlank()) return NONE
        runCatching { return DATE.format(LocalDate.parse(v.take(10))) }
        return NONE
    }

    fun date(d: LocalDate?): String = d?.let { DATE.format(it) } ?: NONE

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun period(from: LocalDate?, to: LocalDate?): String = when {
        from == null && to == null -> tr("All dates")
        from == null -> tr("Up to %1\$s", date(to))
        to == null -> tr("From %1\$s", date(from))
        else -> tr("%1\$s to %2\$s", date(from), date(to))
    }

    /** "1234567.50" -> "12,34,567.50" (Indian grouping: last 3 digits, then pairs). */
    internal fun grouped(plain: String): String {
        val dot = plain.indexOf('.')
        val int = if (dot < 0) plain else plain.substring(0, dot)
        val frac = if (dot < 0) "" else plain.substring(dot)
        if (int.length <= 3) return int + frac
        val last3 = int.takeLast(3)
        val rest = int.dropLast(3)
        val sb = StringBuilder()
        val first = rest.length % 2
        if (first > 0) sb.append(rest, 0, first)
        var i = first
        while (i < rest.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(rest, i, i + 2)
            i += 2
        }
        return "$sb,$last3$frac"
    }

    fun sum(values: List<String?>): BigDecimal = values.mapNotNull { parse(it) }.fold(BigDecimal.ZERO, BigDecimal::add)

    /**
     * A file name made safe for Android's file pickers, WhatsApp and Gmail: letters (Hindi included), digits, '-'
     * and '_'. Hindi vowel signs (\p{M}) are kept with their letters.
     */
    fun fileName(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString("_") { p ->
            p!!.trim().replace(Regex("[^\\p{L}\\p{M}\\p{N}-]+"), "-").trim('-').take(40).trimEnd('-')
        }.ifEmpty { "document" } + ".pdf"
}
