package app.stocklist.app

import app.stocklist.app.core.util.Validators
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.ListCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** The app must calculate exactly like the server. Both run the same calc-vectors.csv. */
class ListCalculatorTest {
    private fun backendFile(path: String): File {
        val f = listOf("../../backend", "../backend").map { File(it, path) }.firstOrNull { it.exists() }
        assertNotNull("backend project must sit next to the Android project ($path)", f)
        return f!!
    }

    @Test
    fun sharedVectorsMatchTheServer() {
        val rows = backendFile("src/test/resources/calc-vectors.csv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(rows.size >= 10)
        rows.forEach { row ->
            val c = row.split(",").map { it.trim() }
            val qty = BigDecimal(c[1])
            val price = c[2].takeIf { it.isNotEmpty() }?.let(::BigDecimal)
            val total = ListCalculator.lineTotal(c[0], qty, price)
            val unit = ListCalculator.effectiveUnitPrice(c[0], qty, price)
            assertEquals("line total for $row", c[3].ifEmpty { null }, total?.toPlainString())
            if (c.getOrNull(4).isNullOrEmpty()) assertNull(unit)
            else assertEquals("unit price for $row", 0, BigDecimal(c[4]).compareTo(unit))
        }
    }

    @Test
    fun totalAmountIsNeverMultipliedByQuantity() {
        assertEquals(BigDecimal("2800.00"), ListCalculator.lineTotal(ListCalculator.TOTAL, BigDecimal("20"), BigDecimal("2800")))
        assertEquals(BigDecimal("56000.00"), ListCalculator.lineTotal(ListCalculator.PER_UNIT, BigDecimal("20"), BigDecimal("2800")))
    }

    @Test
    fun decimalQuantitiesAndListTotal() {
        val a = ListCalculator.lineTotal(ListCalculator.PER_UNIT, BigDecimal("2.5"), BigDecimal("48.40"))!! // 121.00
        val b = ListCalculator.lineTotal(ListCalculator.TOTAL, BigDecimal("0.750"), BigDecimal("99.99"))!!
        assertEquals(BigDecimal("220.99"), ListCalculator.listTotal(listOf(a, null, b)))
        assertNull(ListCalculator.listTotal(listOf(null, null)))
    }

    @Test
    fun builtInUnitsMatchTheServerMigration() {
        val sql = backendFile("src/main/resources/db/migration/V2__catalogue_lists_sync.sql").readText()
        val rows = Regex("""\(UNHEX\('([0-9A-F]{32})'\),\s*NULL,\s*'([A-Z]+)',\s*'([^']+)'""").findAll(sql)
            .map { m ->
                val hex = m.groupValues[1].lowercase()
                Triple("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}",
                    m.groupValues[2], m.groupValues[3])
            }.toList()
        assertEquals(14, rows.size)
        assertEquals(rows, BuiltInUnits.all.map { Triple(it.id, it.code, it.name) })
    }
}

class DecimalsTest {
    @Test
    fun parsesAndRejects() {
        assertEquals(BigDecimal("1.5"), Decimals.parse("1.5", "Qty", 3, false))
        assertNull(Decimals.parse("  ", "Qty", 3, false))
        assertThrows(IllegalArgumentException::class.java) { Decimals.parse("1.2345", "Qty", 3, false) }
        assertThrows(IllegalArgumentException::class.java) { Decimals.parse("0", "Qty", 3, false) }
        assertThrows(IllegalArgumentException::class.java) { Decimals.parse("-4", "Qty", 3, false) }
        assertThrows(IllegalArgumentException::class.java) { Decimals.parse("abc", "Qty", 3, false) }
        assertThrows(IllegalArgumentException::class.java) { Decimals.parse("1e3", "Qty", 3, false) }
        assertEquals(BigDecimal.ZERO, Decimals.parse("0", "Price", 2, true))
    }

    @Test
    fun formats() {
        assertEquals("100", Decimals.format(BigDecimal("100.000")))
        assertEquals("1.5", Decimals.format(BigDecimal("1.500")))
        assertEquals("10.00", Decimals.money(BigDecimal("9.995")))
    }

    @Test
    fun phoneRuleMatchesServer() {
        assertNull(Validators.phone(""))
        assertNull(Validators.phone("9876543210"))
        assertNull(Validators.phone("+919876543210"))
        assertNull(Validators.phone("0442345678"))
        assertNotNull(Validators.phone("12345"))
        assertNotNull(Validators.phone("98765-43210"))
    }
}
