package app.stocklist.app

import app.stocklist.app.domain.catalogue.ExpiryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Date calculations and expiry filters, from the same table the server runs. */
class ExpiryCalculatorTest {
    private val filters = listOf(ExpiryCalculator.Filter.EXPIRED, ExpiryCalculator.Filter.WITHIN_7,
        ExpiryCalculator.Filter.WITHIN_30, ExpiryCalculator.Filter.WITHIN_90, ExpiryCalculator.Filter.ALL)

    @Test
    fun sharedVectorsMatchTheServer() {
        val f = listOf("../../backend", "../backend").map { File(it, "src/test/resources/expiry-vectors.csv") }.firstOrNull { it.exists() }
        assertNotNull("backend project must sit next to the Android project", f)
        val rows = f!!.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
        assertTrue(rows.size >= 12)
        rows.forEach { c ->
            val today = LocalDate.parse(c[0])
            val expiry = c[1].takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
            val row = c.joinToString(",")
            assertEquals("days $row", c[2], ExpiryCalculator.daysLeft(expiry, today)?.toString() ?: "")
            filters.forEachIndexed { i, filter ->
                assertEquals("$filter $row", c[3 + i].toBoolean(), ExpiryCalculator.matches(filter, expiry, today))
            }
            assertEquals("label $row", c[8], ExpiryCalculator.label(expiry, today))
        }
    }

    /** The ranges the database queries use select exactly what the rules accept. */
    @Test
    fun queryRangesAgreeWithTheRules() {
        val today = LocalDate.of(2026, 11, 14)
        filters.forEach { f ->
            val (from, to) = ExpiryCalculator.range(f, today)
            for (d in -200L..200L) {
                val e = today.plusDays(d)
                val inRange = (from == null || !e.isBefore(from)) && (to == null || !e.isAfter(to))
                assertEquals("$f at $d days", ExpiryCalculator.matches(f, e, today), inRange)
            }
        }
    }

    /** Dates are stored as yyyy-MM-dd text; text order must equal date order for the database filters to work. */
    @Test
    fun storedDateTextSortsLikeDates() {
        val dates = (0..800).map { LocalDate.of(2026, 1, 1).plusDays(it * 3L) }
        assertEquals(dates.map { it.toString() }.sorted(), dates.sorted().map { it.toString() })
    }
}
