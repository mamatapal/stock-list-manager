package app.stocklist.app.feature.pdf

import app.stocklist.app.core.i18n.trKey

import app.stocklist.app.domain.catalogue.ExpiryCalculator
import java.time.LocalDate

/** Which PDF to make. Travels as navigation arguments, so a preview survives rotation and process death. */
sealed interface PdfJob {
    data class StockList(val id: String) : PdfJob
    /** [sellingOnly]: the selling-price list (no cost, no profit) instead of the full purchase document. */
    data class Purchase(val id: String, val sellingOnly: Boolean = false) : PdfJob
    data class Supplier(val id: String, val from: LocalDate?, val to: LocalDate?) : PdfJob
    data class Item(val id: String, val from: LocalDate?, val to: LocalDate?) : PdfJob
    data class DateRange(val from: LocalDate?, val to: LocalDate?) : PdfJob
    data class Expiry(val filter: ExpiryCalculator.Filter) : PdfJob
    /** A server report (see [ReportKind]); always needs a period. */
    data class Report(val kind: ReportKind, val from: LocalDate, val to: LocalDate) : PdfJob

    val route: String
        get() {
            val (kind, id, from, to, filter) = when (this) {
                is StockList -> Args("LIST", id)
                is Purchase -> Args("PURCHASE", id, filter = if (sellingOnly) SELLING_ONLY else null)
                is Supplier -> Args("SUPPLIER", id, from, to)
                is Item -> Args("ITEM", id, from, to)
                is DateRange -> Args("RANGE", null, from, to)
                is Expiry -> Args("EXPIRY", null, filter = filter.name)
                is Report -> Args("REPORT", null, from, to, kind.name)
            }
            return "pdf?job=$kind" + listOfNotNull(id?.let { "id=$it" }, from?.let { "from=$it" }, to?.let { "to=$it" },
                filter?.let { "filter=$it" }).joinToString("") { "&$it" }
        }

    private data class Args(val kind: String, val id: String?, val from: LocalDate? = null, val to: LocalDate? = null,
                            val filter: String? = null)

    companion object {
        const val SELLING_ONLY = "SELLING"
        const val ROUTE = "pdf?job={job}&id={id}&from={from}&to={to}&filter={filter}"

        fun parse(job: String?, id: String?, from: String?, to: String?, filter: String?): PdfJob? {
            val f = from?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val t = to?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return when (job) {
                "LIST" -> id?.let(::StockList)
                "PURCHASE" -> id?.let { Purchase(it, sellingOnly = filter == SELLING_ONLY) }
                "SUPPLIER" -> id?.let { Supplier(it, f, t) }
                "ITEM" -> id?.let { Item(it, f, t) }
                "RANGE" -> DateRange(f, t)
                "EXPIRY" -> runCatching { ExpiryCalculator.Filter.valueOf(filter!!) }.getOrNull()?.let(::Expiry)
                "REPORT" -> {
                    val k = runCatching { ReportKind.valueOf(filter!!) }.getOrNull()
                    if (k != null && f != null && t != null) Report(k, f, t) else null
                }
                else -> null
            }
        }
    }
}

enum class ReportKind(val title: String) {
    PURCHASES(trKey("Purchases")), SUPPLIER_TOTALS(trKey("Supplier totals")), ITEM_PRICES(trKey("Item prices")), EXPECTED_PROFIT(trKey("Expected profit"))
}
