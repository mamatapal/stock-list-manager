package app.stocklist.app.domain.catalogue

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Expiry rules, identical to the server's ExpiryCalculator (both run expiry-vectors.csv).
 * days left = expiry date - today. EXPIRED: below 0. WITHIN_n: 0..n inclusive (today counts; the windows overlap).
 * ALL: every batch with an expiry date. Batches without an expiry date are in no expiry list.
 */
object ExpiryCalculator {
    enum class Filter(val label: String) {
        EXPIRED("Expired"), WITHIN_7("Within 7 days"), WITHIN_30("Within 30 days"), WITHIN_90("Within 90 days"), ALL("All")
    }

    fun daysLeft(expiry: LocalDate?, today: LocalDate): Long? = expiry?.let { ChronoUnit.DAYS.between(today, it) }

    fun matches(filter: Filter, expiry: LocalDate?, today: LocalDate): Boolean {
        val d = daysLeft(expiry, today) ?: return false
        return when (filter) {
            Filter.EXPIRED -> d < 0
            Filter.WITHIN_7 -> d in 0..7
            Filter.WITHIN_30 -> d in 0..30
            Filter.WITHIN_90 -> d in 0..90
            Filter.ALL -> true
        }
    }

    /** Inclusive [from, to] for database queries; null = open. */
    fun range(filter: Filter, today: LocalDate): Pair<LocalDate?, LocalDate?> = when (filter) {
        Filter.EXPIRED -> null to today.minusDays(1)
        Filter.WITHIN_7 -> today to today.plusDays(7)
        Filter.WITHIN_30 -> today to today.plusDays(30)
        Filter.WITHIN_90 -> today to today.plusDays(90)
        Filter.ALL -> null to null
    }

    fun label(expiry: LocalDate?, today: LocalDate): String {
        val d = daysLeft(expiry, today) ?: return "No expiry date"
        return when {
            d < 0 -> "Expired ${-d} " + if (d == -1L) "day ago" else "days ago"
            d == 0L -> "Expires today"
            else -> "Expires in $d " + if (d == 1L) "day" else "days"
        }
    }
}
