package app.stocklist.app

import app.stocklist.app.core.pdf.PdfFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PdfFormatTest {
    @Test
    fun rupeesWithIndianGroupingAndTwoDecimals() {
        assertEquals("₹0.00", PdfFormat.money("0"))
        assertEquals("₹5.50", PdfFormat.money("5.5"))
        assertEquals("₹999.00", PdfFormat.money("999"))
        assertEquals("₹1,000.00", PdfFormat.money("1000"))
        assertEquals("₹12,345.68", PdfFormat.money("12345.675"))
        assertEquals("₹1,23,456.50", PdfFormat.money("123456.5"))
        assertEquals("₹12,34,56,789.00", PdfFormat.money("123456789"))
        assertEquals("-₹250.00", PdfFormat.money("-250"))
        assertEquals("-", PdfFormat.money(null))
        assertEquals("-", PdfFormat.money("not a number"))
        assertEquals("₹", PdfFormat.RUPEE)
    }

    @Test
    fun unitPricesKeepUpToFourDecimals() {
        assertEquals("₹12.50", PdfFormat.unitPrice("12.5"))
        assertEquals("₹0.3333", PdfFormat.unitPrice("0.3333"))
        assertEquals("₹0.125", PdfFormat.unitPrice("0.1250"))
        assertEquals("₹1,500.00", PdfFormat.unitPrice("1500.0000"))
        assertEquals("₹0.3334", PdfFormat.unitPrice("0.33335"))
    }

    @Test
    fun quantitiesWithoutTrailingZeros() {
        assertEquals("2.5", PdfFormat.qty("2.500"))
        assertEquals("0.125", PdfFormat.qty("0.125"))
        assertEquals("100", PdfFormat.qty("100.000"))
        assertEquals("100", PdfFormat.qty("1E+2"))
        assertEquals("1,250.75", PdfFormat.qty("1250.75"))
        assertEquals("2.5 kg", PdfFormat.qty("2.500", "kg"))
        assertEquals("-", PdfFormat.qty(null, "kg"))
    }

    @Test
    fun datesAreDdMmYyyy() {
        assertEquals("05-11-2026", PdfFormat.date("2026-11-05"))
        assertEquals("05-11-2026", PdfFormat.date("2026-11-05T10:15:30Z"))
        assertEquals("29-02-2028", PdfFormat.date(LocalDate.of(2028, 2, 29)))
        assertEquals("-", PdfFormat.date("31-12-2026"))
        assertEquals("-", PdfFormat.date(null as String?))
        assertEquals("01-10-2026 to 30-11-2026", PdfFormat.period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)))
        assertEquals("All dates", PdfFormat.period(null, null))
        assertEquals("16.03%", PdfFormat.percent("16.0305"))
    }

    @Test
    fun fileNamesAreSafeForEveryApp() {
        assertEquals("Purchase_K-1-23_2026-11-14.pdf", PdfFormat.fileName("Purchase", "K/1 23", "2026-11-14"))
        assertEquals("List_Monday-s-order.pdf", PdfFormat.fileName("List", "Monday's order ", null))
        assertEquals(true, PdfFormat.fileName("Item", PdfFixtures.LONG_NAME).length < 60)
        assertEquals("document.pdf", PdfFormat.fileName(null, ""))
    }
}
