package app.stocklist.app

import app.stocklist.app.PdfFixtures.batch
import app.stocklist.app.PdfFixtures.ctx
import app.stocklist.app.PdfFixtures.history
import app.stocklist.app.PdfFixtures.line
import app.stocklist.app.PdfFixtures.purchase
import app.stocklist.app.core.pdf.Facts
import app.stocklist.app.core.pdf.Heading
import app.stocklist.app.core.pdf.Note
import app.stocklist.app.core.pdf.ReportDoc
import app.stocklist.app.core.pdf.Table
import app.stocklist.app.data.remote.ExpectedProfitReportDto
import app.stocklist.app.data.remote.PeriodDto
import app.stocklist.app.data.remote.ProfitRowDto
import app.stocklist.app.data.remote.PurchaseReportDto
import app.stocklist.app.data.remote.PurchaseReportRowDto
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import app.stocklist.app.feature.pdf.PdfDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What each PDF says: numbers, labels, what is counted, and what a user without profit permission never sees. */
class PdfDocumentsTest {
    private fun ReportDoc.tables() = blocks.filterIsInstance<Table>()
    private fun ReportDoc.facts() = blocks.filterIsInstance<Facts>().flatMap { it.items }.toMap()
    private fun ReportDoc.allText(): String = (listOf(title) + subtitle + blocks.flatMap { b ->
        when (b) {
            is Table -> b.columns.map { it.title } + (b.rows + b.totals).flatMap { it.cells } + b.emptyText
            is Facts -> b.items.filter { !it.second.isNullOrBlank() }.flatMap { listOf(it.first, it.second!!) }
            is Note -> listOf(b.text)
            is Heading -> listOf(b.text)
        }
    }).joinToString("\n")

    @Test
    fun stockListKeepsLongNamesAndDescriptionsWhole() {
        val lines = listOf(
            PdfFixtures.listLine(0, "Rice", "25.500", "48", "1224.00"),
            PdfFixtures.listLine(1, PdfFixtures.LONG_NAME, "2", null, null, description = "Only the 5 litre tin"),
        )
        val doc = PdfDocuments.stockList(ctx(), PdfFixtures.list(), lines, PdfFixtures.supplier(), mapOf("u-kg" to "kg"))
        val t = doc.tables().single()
        assertEquals(listOf("1", "Rice", "25.5 kg", "₹48.00 / kg", "₹1,224.00"), t.rows[0].cells)
        assertEquals(PdfFixtures.LONG_NAME + "\nOnly the 5 litre tin", t.rows[1].cells[1])
        assertEquals("-", t.rows[1].cells[3])
        assertEquals("₹1,224.00", t.totals.single().cells.last())
        assertEquals("14-11-2026", doc.facts()["List date"])
        assertEquals("Asha Sharma", doc.facts()["Prepared by"])
        assertTrue(doc.allText().contains("Deliver before 10 am"))
        assertTrue(doc.fileName.endsWith(".pdf"))
    }

    @Test
    fun emptyDocumentsSaySo() {
        assertEquals("This list has no items",
            PdfDocuments.stockList(ctx(), PdfFixtures.list(), emptyList(), null, emptyMap()).tables().single().emptyText)
        val range = PdfDocuments.dateRangeReport(ctx(), emptyList(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        assertTrue(range.tables().all { it.rows.isEmpty() })
        assertEquals("₹0.00", range.facts()["Total cost"])
        assertEquals("No batches in this list",
            PdfDocuments.expiryReport(ctx(), emptyList(), ExpiryCalculator.Filter.EXPIRED, LocalDate.now()).tables().single().emptyText)
    }

    @Test
    fun purchaseShowsProfitOnlyWithPermissionAndCallsItExpected() {
        val p = purchase("p1", "K-1", "2026-11-10", cost = "2450.00", profit = "550.00", items = 2)
        val lines = listOf(line("a", "p1", "Rice", "25", "kg", "48", "1200.00", "58", "250.00"),
            line("b", "p1", "Oil", "12.5", "L", "100", "1250.00", "124", "300.00", order = 1))
        val d = PdfFixtures.delivery("d1", "p1", "2026-11-11")
        val dl = listOf(PdfFixtures.deliveryLine("x1", "d1", "p1", "a", "25", "B-7", "2027-05-31"),
            PdfFixtures.deliveryLine("x2", "d1", "p1", "b", "10"))

        val withProfit = PdfDocuments.purchase(ctx(profit = true), p, lines, listOf(d), dl, PdfFixtures.supplier())
        val t = withProfit.tables().first()
        assertTrue(t.columns.any { it.title == "Expected profit" })
        assertFalse(withProfit.allText().contains("Actual profit"))
        assertTrue(withProfit.allText().contains("not realised profit"))
        assertEquals(listOf("2", "Oil", "12.5 L", "10", "₹100.00", "₹1,250.00", "₹124.00", "₹300.00"), t.rows[1].cells)
        assertEquals("₹2,450.00", t.totals.single().cells[5])
        assertEquals("₹550.00", t.totals.single().cells[7])
        val deliveries = withProfit.tables()[1]
        assertEquals(listOf("11-11-2026", "Ravi Kumar", "Rice", "25 kg", "B-7", "31-05-2027"), deliveries.rows[0].cells)

        val without = PdfDocuments.purchase(ctx(profit = false), p, lines, listOf(d), dl, PdfFixtures.supplier())
        assertFalse(without.allText().contains("profit", ignoreCase = true))
        assertFalse(without.allText().contains("550"))
        assertFalse(without.allText().contains("Selling price"))
    }

    @Test
    fun cancelledPurchasesAreMarkedAndNeverCounted() {
        val cancelled = purchase("p1", "K-9", "2026-11-10", status = "CANCELLED", cost = "999.00")
        val doc = PdfDocuments.purchase(ctx(), cancelled, emptyList(), emptyList(), emptyList(), null)
        val warning = doc.blocks.filterIsInstance<Note>().first()
        assertTrue(warning.warning)
        assertTrue(warning.text.startsWith("CANCELLED"))
        assertTrue(warning.text.contains("Entered twice"))

        val list = listOf(purchase("a", "A-1", "2026-11-01", cost = "500.00", profit = "100.00"), cancelled,
            purchase("b", "A-2", "2026-11-05", status = "PARTIAL", cost = "300.50", profit = "50.25"),
            purchase("c", "draft", "2026-11-06", status = "DRAFT", cost = "1.00"))
        val report = PdfDocuments.supplierReport(ctx(), PdfFixtures.supplier(), list, null, null)
        val t = report.tables().single()
        assertEquals("drafts are not history; oldest first", listOf("A-1", "A-2", "K-9"), t.rows.map { it.cells[1] })
        assertTrue(t.rows[2].muted)
        assertEquals(listOf("Total", "2 purchases", "", "", "₹800.50", "₹150.25"), t.totals.single().cells)
        assertEquals("₹400.25", report.facts()["Average purchase"])
        assertEquals("01-11-2026", report.facts()["First purchase"])
    }

    @Test
    fun dateRangeReportTotalsBySupplier() {
        val ps = listOf(
            purchase("a", "A-1", "2026-11-01", cost = "600.00", supplierId = "s1", supplierName = "Agarwal"),
            purchase("b", "B-1", "2026-11-02", cost = "300.00", supplierId = "s2", supplierName = "Bansal"),
            purchase("c", "A-2", "2026-11-03", cost = "100.00", supplierId = "s1", supplierName = "Agarwal"),
            purchase("x", "B-x", "2026-11-04", status = "CANCELLED", cost = "5000.00", supplierId = "s2", supplierName = "Bansal"),
        )
        val doc = PdfDocuments.dateRangeReport(ctx(profit = false), ps, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        val (purchases, suppliers) = doc.tables()
        assertEquals(4, purchases.rows.size)
        assertEquals("₹1,000.00", purchases.totals.single().cells[5])
        assertEquals(listOf(listOf("Agarwal", "2", "₹700.00", "70.00%"), listOf("Bansal", "1", "₹300.00", "30.00%")),
            suppliers.rows.map { it.cells })
        assertEquals("100.00%", suppliers.totals.single().cells[3])
        assertFalse(doc.allText().contains("profit", ignoreCase = true))
        assertEquals("01-11-2026 to 30-11-2026", doc.subtitle.single())
    }

    @Test
    fun itemReportPricesAndMixedUnits() {
        val rows = listOf(history("p1", "2026-10-01", "10", "50", "500.00"), history("p2", "2026-11-01", "5", "60", "300.00"),
            history("p3", "2026-11-05", "2", "55", "110.00", supplier = "Bansal"),
            history("p4", "2026-11-06", "100", "1", "100.00", status = "CANCELLED"))
        val doc = PdfDocuments.itemReport(ctx(), PdfFixtures.item(), rows, null, null)
        val f = doc.facts()
        assertEquals("₹50.00", f["Lowest unit cost"])
        assertEquals("₹60.00", f["Highest unit cost"])
        assertEquals("910 / 17", "₹53.5294 (weighted)", f["Average unit cost"])
        assertEquals("₹55.00", f["Latest unit cost"])
        val t = doc.tables().single()
        assertEquals("17 kg", t.totals.single().cells[3])
        assertEquals("₹910.00", t.totals.single().cells[6])

        val mixed = PdfDocuments.itemReport(ctx(), PdfFixtures.item(), rows + history("p5", "2026-11-07", "1", "500", "500.00",
            unitId = "u-bag", unit = "bag"), null, null)
        assertEquals("Mixed units", mixed.tables().single().totals.single().cells[3])
        assertTrue(mixed.allText().contains("more than one unit"))
    }

    @Test
    fun expiryReportLabelsDaysLeft() {
        val today = LocalDate.of(2026, 11, 14)
        val doc = PdfDocuments.expiryReport(ctx(), listOf(batch("1", "Milk", "2026-11-10"), batch("2", "Bread", "2026-11-14"),
            batch("3", "Jam", "2026-12-01", qty = "2.5")), ExpiryCalculator.Filter.ALL, today)
        val t = doc.tables().single()
        assertEquals("Expired 4 days ago", t.rows[0].cells[3])
        assertTrue(t.rows[0].bold)
        assertEquals("Expires today", t.rows[1].cells[3])
        assertEquals("2.5 pcs", t.rows[2].cells[4])
        assertEquals("1", doc.facts()["Already expired"])
        assertEquals("14-11-2026", doc.facts()["As on"])
    }

    @Test
    fun serverReportsKeepTheirLabels() {
        val r = PurchaseReportDto(PeriodDto("2026-10-01", "2026-11-30"), 1, 0, "1210.00", null, null, false, null,
            listOf(PurchaseReportRowDto("p", "2026-11-05", "B-1", "s", "Bansal", "RECEIVED", 2, "1210.00")))
        val doc = PdfDocuments.purchaseReport(ctx(profit = true), r)
        assertFalse("the server left profit out, so the PDF does too", doc.allText().contains("profit", ignoreCase = true))

        val p = ExpectedProfitReportDto(PeriodDto("2026-10-01", "2026-11-30"), 3, "1210.00", "1100.00", "110.00", "1310.00",
            "210.00", "16.03", "Expected profit is an estimate. It is not realised profit.",
            listOf(ProfitRowDto("s", "Agarwal", "800.00", "950.00", "150.00", "15.79")), emptyList())
        val pd = PdfDocuments.expectedProfit(ctx(), p)
        assertEquals("Expected profit report", pd.title)
        assertTrue(pd.subtitle.contains("Estimate only, not realised profit"))
        assertTrue((pd.blocks.first() as Note).warning)
        assertEquals("₹210.00", pd.facts()["Expected profit"])
        assertEquals("16.03%", pd.facts()["Expected margin"])
        assertFalse(pd.allText().contains("Actual profit") || pd.allText().contains("Net profit"))
    }
}
