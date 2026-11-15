package app.stocklist.app

import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.catalogue.ReceivingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** The phone must calculate exactly like the server: both run the same tables from the backend project. */
class PurchaseCalculatorTest {
    private fun rows(name: String): List<List<String>> {
        val f = listOf("../../backend", "../backend").map { File(it, "src/test/resources/$name") }.firstOrNull { it.exists() }
        assertNotNull("backend project must sit next to the Android project ($name)", f)
        return f!!.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    }

    private fun num(s: String) = if (s.isEmpty()) null else BigDecimal(s)
    private fun str(d: BigDecimal?) = d?.toPlainString() ?: ""

    @Test
    fun purchaseVectorsMatchTheServer() {
        val rows = rows("purchase-calc-vectors.csv")
        assertTrue(rows.size >= 10)
        rows.forEach { c ->
            val l = PurchaseCalculator.line(c[0], BigDecimal(c[1]), num(c[2]), num(c[3]))
            val row = c.joinToString(",")
            assertEquals("cost $row", c[4], str(l.cost))
            assertEquals("unit price $row", c[5], str(l.unitPrice))
            assertEquals("sales $row", c[6], str(l.sales))
            assertEquals("profit $row", c[7], str(l.profit))
            assertEquals("margin $row", c[8], str(l.margin))
            assertEquals("markup $row", c[9], str(l.markup))
        }
    }

    @Test
    fun specExamples() {
        val oil = PurchaseCalculator.line("PER_UNIT", BigDecimal("20"), BigDecimal("140"), BigDecimal("165"))
        assertEquals(BigDecimal("2800.00"), oil.cost)
        assertEquals(BigDecimal("15.15"), oil.margin)
        assertEquals(BigDecimal("17.86"), oil.markup)
        val rice = PurchaseCalculator.line("TOTAL", BigDecimal("1.5"), BigDecimal("270"), null)
        assertEquals("a total amount is never multiplied", BigDecimal("270.00"), rice.cost)
        val soap = PurchaseCalculator.line("PER_UNIT", BigDecimal("10"), BigDecimal("5"), BigDecimal.ZERO)
        assertNull("margin N/A", soap.margin)
        assertEquals(BigDecimal("-100.00"), soap.markup)
        val t = PurchaseCalculator.totals(listOf(oil, rice, soap))
        assertEquals(BigDecimal("3120.00"), t.cost)
        assertEquals(BigDecimal("450.00"), t.profit)
        assertEquals(BigDecimal("13.64"), t.margin)
        assertEquals(BigDecimal("15.79"), t.markup)
    }

    @Test
    fun receivingVectorsMatchTheServer() {
        rows("receiving-vectors.csv").forEach { c ->
            val p = ReceivingCalculator.progress(BigDecimal(c[0]), BigDecimal(c[1]))
            val row = c.joinToString(",")
            assertEquals("remaining $row", 0, BigDecimal(c[2]).compareTo(p.remaining))
            assertEquals("over by $row", 0, BigDecimal(c[3]).compareTo(p.overBy))
            assertEquals("status $row", c[4], p.status)
        }
        assertEquals("PARTIAL", ReceivingCalculator.overall(listOf("FULL", "PENDING")))
        assertEquals("FULL", ReceivingCalculator.overall(listOf("FULL", "FULL")))
        assertEquals("PENDING", ReceivingCalculator.overall(emptyList()))
    }
}
