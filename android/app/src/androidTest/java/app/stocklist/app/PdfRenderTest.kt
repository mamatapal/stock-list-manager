package app.stocklist.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stocklist.app.PdfFixtures.ctx
import app.stocklist.app.PdfFixtures.purchase
import app.stocklist.app.core.pdf.Align
import app.stocklist.app.core.pdf.Column
import app.stocklist.app.core.pdf.ReportDoc
import app.stocklist.app.core.pdf.ReportPdf
import app.stocklist.app.core.pdf.Row
import app.stocklist.app.core.pdf.Table
import app.stocklist.app.feature.pdf.PdfDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Draws real PDFs and opens them again with Android's own PDF reader: empty, large, long names, many pages. */
@RunWith(AndroidJUnit4::class)
class PdfRenderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun write(doc: ReportDoc): Pair<File, ReportPdf.Result> {
        val f = File(context.cacheDir, "test-${System.nanoTime()}.pdf")
        val r = f.outputStream().use { ReportPdf.write(doc, it) }
        return f to r
    }

    private fun <T> open(f: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(block) }

    private fun text(f: File): List<String> = open(f) { r ->
        (0 until r.pageCount).map { i -> r.openPage(i).use { p -> p.textContents.joinToString("\n") { it.text } } }
    }

    private fun inkOnPage(r: PdfRenderer, i: Int): Int = r.openPage(i).use { p ->
        val b = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        var dark = 0
        for (y in 0 until b.height step 3) for (x in 0 until b.width step 3) if (b.getPixel(x, y) != Color.WHITE) dark++
        dark
    }

    @Test
    fun emptyReportIsOneA4PageWithItsMessage() {
        val doc = PdfDocuments.dateRangeReport(ctx(), emptyList(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        val (f, r) = write(doc)
        assertEquals(1, r.pageCount)
        open(f) { pdf ->
            assertEquals(1, pdf.pageCount)
            pdf.openPage(0).use { p -> assertEquals(595, p.width); assertEquals(842, p.height) }
            assertTrue("something is drawn", inkOnPage(pdf, 0) > 500)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            val t = text(f).single()
            assertTrue(t, t.contains("No purchases in this period"))
            assertTrue(t, t.contains("Page 1 of 1"))
            assertTrue(t, t.contains("Sharma General Store"))
        }
    }

    @Test
    fun manyRowsRunOverPagesWithTheHeadingRepeated() {
        val ps = (1..300).map { i ->
            purchase("p$i", "INV-$i", LocalDate.of(2026, 1, 1).plusDays((i % 300).toLong()).toString(), cost = "123456.50",
                supplierName = if (i % 7 == 0) PdfFixtures.LONG_NAME else "Supplier $i", supplierId = "s${i % 5}")
        }
        val doc = PdfDocuments.dateRangeReport(ctx(), ps, null, null)
        val (f, r) = write(doc)
        assertTrue("pages: ${r.pageCount}", r.pageCount >= 8)
        val rows = r.pages.flatMap { it.rows }.filter { it.startsWith("row ") }
        // Two tables: 300 purchases and 5 suppliers; every row appears exactly once.
        assertEquals(305, rows.size)
        r.pages.forEach { p -> if (p.rows.any { it.startsWith("row ") }) assertTrue("heading on every page with rows", p.tableHeadings >= 1) }
        open(f) { pdf ->
            assertEquals(r.pageCount, pdf.pageCount)
            for (i in 0 until pdf.pageCount) assertTrue("page ${i + 1} is not blank", inkOnPage(pdf, i) > 500)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            val pages = text(f)
            pages.forEachIndexed { i, t -> assertTrue(t, t.contains("Page ${i + 1} of ${pages.size}")) }
            assertTrue(pages[0], pages[0].contains("₹1,23,456.50"))
            assertTrue("total of 300 x 1,23,456.50", pages.any { it.contains("₹3,70,36,950.00") })
            assertTrue(pages[1], pages[1].contains("(continued)"))
            assertTrue(pages[1], pages[1].contains("Total cost"))
        }
    }

    @Test
    fun aRowTallerThanAPageContinuesOnTheNext() {
        val huge = (1..250).joinToString(" ") { "Line $it of a very long item description." } + " " + PdfFixtures.LONG_NAME.repeat(8)
        val doc = ReportDoc(PdfFixtures.business, "Long text", emptyList(), listOf(
            Table(listOf(Column("Item", 4f), Column("Quantity", 1f, Align.RIGHT)),
                listOf(Row(listOf("Short", "1")), Row(listOf(huge, "2")), Row(listOf("After", "3")))),
        ), "Asha", "14-11-2026 10:00", "long.pdf")
        val (f, r) = write(doc)
        val parts = r.pages.flatMap { it.rows }.filter { it.startsWith("row 1 part") }
        assertTrue("split into $parts", parts.size >= 2)
        assertEquals(listOf("row 0"), r.pages.first().rows.take(1))
        assertEquals("row 2", r.pages.flatMap { it.rows }.last())
        r.pages.drop(1).forEach { assertEquals("heading repeated", 1, it.tableHeadings) }
        open(f) { pdf -> assertEquals(r.pageCount, pdf.pageCount) }
    }

    @Test
    fun longNamesWrapInsteadOfBeingCut() {
        val p = purchase("p1", "K-1", "2026-11-10", cost = "120.00")
        val lines = listOf(PdfFixtures.line("a", "p1", PdfFixtures.LONG_NAME, "2.500", "L", "48", "120.00"))
        val doc = PdfDocuments.purchase(ctx(), p, lines, emptyList(), emptyList(), PdfFixtures.supplier())
        val (f, r) = write(doc)
        assertEquals(1, r.pageCount)
        if (Build.VERSION.SDK_INT >= 35) {
            val t = text(f).single().replace("\n", " ").replace(Regex("\\s+"), " ")
            assertTrue(t, t.contains("not for resale outside Maharashtra"))
            assertTrue(t, t.contains("2.5 L"))
            assertTrue(t, t.contains("10-11-2026"))
        }
    }

    @Test
    fun everyDocumentTypeRenders() {
        val today = LocalDate.of(2026, 11, 14)
        val docs = listOf(
            PdfDocuments.stockList(ctx(), PdfFixtures.list(), (0 until 40).map { PdfFixtures.listLine(it, "Item $it", "1.5", "10", "15.00") },
                PdfFixtures.supplier(), mapOf("u-kg" to "kg")),
            PdfDocuments.supplierReport(ctx(), PdfFixtures.supplier(), emptyList(), null, today),
            PdfDocuments.itemReport(ctx(), PdfFixtures.item(), emptyList(), today.minusDays(30), today),
            PdfDocuments.expiryReport(ctx(), (1..80).map { PdfFixtures.batch("$it", "Item $it", today.plusDays(it.toLong()).toString()) },
                app.stocklist.app.domain.catalogue.ExpiryCalculator.Filter.WITHIN_90, today),
        )
        docs.forEach { d ->
            val (f, r) = write(d)
            open(f) { pdf -> assertEquals(d.title, r.pageCount, pdf.pageCount) }
        }
    }
}
