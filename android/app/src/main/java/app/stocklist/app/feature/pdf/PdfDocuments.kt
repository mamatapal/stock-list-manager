package app.stocklist.app.feature.pdf

import app.stocklist.app.core.i18n.trMessage

import app.stocklist.app.core.i18n.tr

import app.stocklist.app.core.pdf.Align
import app.stocklist.app.core.pdf.BusinessInfo
import app.stocklist.app.core.pdf.Column
import app.stocklist.app.core.pdf.Facts
import app.stocklist.app.core.pdf.Heading
import app.stocklist.app.core.pdf.Note
import app.stocklist.app.core.pdf.PdfFormat
import app.stocklist.app.core.pdf.PdfFormat.date
import app.stocklist.app.core.pdf.PdfFormat.money
import app.stocklist.app.core.pdf.PdfFormat.qty
import app.stocklist.app.core.pdf.PdfFormat.unitPrice
import app.stocklist.app.core.pdf.ReportDoc
import app.stocklist.app.core.pdf.Row
import app.stocklist.app.core.pdf.Table
import app.stocklist.app.data.local.BatchEntity
import app.stocklist.app.data.local.DeliveryEntity
import app.stocklist.app.data.local.DeliveryLineEntity
import app.stocklist.app.data.local.ItemEntity
import app.stocklist.app.data.local.ItemHistoryRow
import app.stocklist.app.data.local.PurchaseEntity
import app.stocklist.app.data.local.PurchaseLineEntity
import app.stocklist.app.data.local.StockListEntity
import app.stocklist.app.data.local.StockListLineEntity
import app.stocklist.app.data.local.SupplierEntity
import app.stocklist.app.data.remote.ExpectedProfitReportDto
import app.stocklist.app.data.remote.ItemPricesDto
import app.stocklist.app.data.remote.PurchaseReportDto
import app.stocklist.app.data.remote.SupplierTotalsDto
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Who is printing, for whom, and what they may see. [showProfit] comes from EXPECTED_PROFIT_VIEW. */
data class PdfContext(
    val business: BusinessInfo,
    val generatedBy: String?,
    val generatedAt: Long,
    val people: Map<String, String> = emptyMap(),
    val showProfit: Boolean = false,
) {
    fun name(id: String?): String? = id?.let { people[it] }
}

/**
 * Builds the printable documents from the phone's data (or, for the reports, from the server's report). Pure
 * Kotlin: unit tests check every number and label without drawing anything.
 */
object PdfDocuments {
    const val PROFIT_NOTE = "Expected profit is an estimate: selling price minus purchase cost, for lines that have a selling price. It is not realised profit; actual sales are not recorded in this app."

    fun purchaseStatus(s: String): String = when (s) {
        "DRAFT" -> tr("Draft")
        "PENDING" -> tr("Awaiting goods")
        "PARTIAL" -> tr("Partly received")
        "RECEIVED" -> tr("Received")
        "CANCELLED" -> tr("Cancelled")
        else -> s.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun label(s: String) = s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun doc(ctx: PdfContext, title: String, subtitle: List<String>, file: String, vararg blocks: app.stocklist.app.core.pdf.Block?) =
        ReportDoc(ctx.business, title, subtitle, blocks.filterNotNull(), ctx.generatedBy,
            PdfFormat.dateTime(ctx.generatedAt), file)

    private fun counted(p: PurchaseEntity) = p.status != "CANCELLED" && p.status != "DRAFT" && !p.deleted

    private fun BigDecimal.m() = money(this)

    // ------------------------------------------------------------------------------------- 1. out-of-stock list

    fun stockList(ctx: PdfContext, list: StockListEntity, lines: List<StockListLineEntity>, supplier: SupplierEntity?,
                  units: Map<String, String>): ReportDoc {
        val rows = lines.sortedBy { it.sortOrder }.mapIndexed { i, l ->
            val unit = units[l.unitId]
            val price = when {
                l.enteredPrice.isNullOrBlank() -> PdfFormat.NONE
                l.priceBasis == "TOTAL" -> tr("%1\$s total", unitPrice(l.enteredPrice))
                else -> unitPrice(l.enteredPrice) + (unit?.let { " / $it" } ?: "")
            }
            Row(listOf((i + 1).toString(), l.itemName + (l.description?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
                qty(l.quantity, unit), price, money(l.lineTotal)))
        }
        val total = list.totalAmount ?: PdfFormat.sum(lines.map { it.lineTotal }).toPlainString()
        return doc(ctx, tr("Out-of-stock list"), listOf(list.title), PdfFormat.fileName(tr("List"), list.title, list.listDate),
            Facts(listOf(
                tr("List date") to date(list.listDate),
                tr("Supplier") to supplier?.let { s -> listOfNotNull(s.name, s.mobile, s.city).joinToString(", ") },
                tr("Status") to label(list.status),
                tr("Items") to lines.size.toString(),
                tr("Prepared by") to ctx.name(list.createdBy),
            )),
            list.notes?.takeIf { it.isNotBlank() }?.let { Note(tr("Notes: %1\$s", it)) },
            Table(
                listOf(Column("#", 0.5f, Align.RIGHT), Column(tr("Item"), 4.6f), Column(tr("Quantity"), 1.5f, Align.RIGHT),
                    Column(tr("Price"), 1.7f, Align.RIGHT), Column(tr("Amount"), 1.6f, Align.RIGHT)),
                rows,
                totals = listOf(Row(listOf("", if (lines.size == 1) tr("Total (1 item)") else tr("Total (%1\$s items)", lines.size), "", "", money(total)))),
                emptyText = tr("This list has no items"),
            ),
        )
    }

    // ------------------------------------------------------------------------------------- 2. purchase

    fun purchase(ctx: PdfContext, p: PurchaseEntity, lines: List<PurchaseLineEntity>, deliveries: List<DeliveryEntity>,
                 deliveryLines: List<DeliveryLineEntity>, supplier: SupplierEntity?): ReportDoc {
        val profit = ctx.showProfit
        val received = deliveryLines.groupBy { it.purchaseLineId }
            .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, d -> a + BigDecimal(d.quantity) } }
        val cols = buildList {
            add(Column("#", 0.45f, Align.RIGHT)); add(Column(tr("Item"), 3.4f)); add(Column(tr("Quantity"), 1.35f, Align.RIGHT))
            add(Column(tr("Received"), 1.2f, Align.RIGHT)); add(Column(tr("Unit cost"), 1.3f, Align.RIGHT))
            add(Column(tr("Amount"), 1.45f, Align.RIGHT))
            if (profit) { add(Column(tr("Selling price"), 1.3f, Align.RIGHT)); add(Column(tr("Expected profit"), 1.4f, Align.RIGHT)) }
        }
        val rows = lines.sortedBy { it.sortOrder }.mapIndexed { i, l ->
            Row(buildList {
                add((i + 1).toString()); add(l.itemName); add(qty(l.quantity, l.unitCode))
                add(qty(received[l.id]?.toPlainString() ?: "0")); add(unitPrice(l.purchaseUnitPrice)); add(money(l.totalCost))
                if (profit) { add(unitPrice(l.sellingPrice)); add(money(l.expectedProfit)) }
            })
        }
        val totals = listOf(Row(buildList {
            add(""); add(tr("Total")); add(""); add(""); add(""); add(money(p.totalCost))
            if (profit) { add(""); add(money(p.totalExpectedProfit)) }
        }))
        val byDelivery = deliveryLines.groupBy { it.deliveryId }
        val lineName = lines.associate { it.id to (it.itemName to it.unitCode) }
        val deliveryRows = deliveries.sortedWith(compareBy({ it.receivedDate }, { it.createdAt })).flatMap { d ->
            byDelivery[d.id].orEmpty().map { dl ->
                val (name, unit) = lineName[dl.purchaseLineId] ?: (tr("Item") to null)
                val correction = dl.kind == "CORRECTION"
                Row(listOf(date(d.receivedDate), ctx.name(d.receivedBy) ?: PdfFormat.NONE,
                    name + (if (correction) "\n" + tr("Correction") + (dl.reason?.let { ": $it" } ?: "") else ""),
                    qty(dl.quantity, unit), dl.batchNo ?: PdfFormat.NONE, date(dl.expiryDate)),
                    muted = d.pending)
            }
        }
        val cancelled = p.status == "CANCELLED"
        return doc(ctx, tr("Purchase"), listOfNotNull(
            listOfNotNull(p.supplierName ?: supplier?.name, p.invoiceNo?.let { tr("Invoice %1\$s", it) }).joinToString("  ·  ")),
            PdfFormat.fileName(tr("Purchase"), p.invoiceNo ?: p.supplierName, p.purchaseDate),
            if (cancelled) Note(tr("CANCELLED. This purchase is not counted in any total.") +
                (p.cancelReason?.let { tr(" Reason: %1\$s", it) } ?: ""), warning = true) else null,
            Facts(listOf(
                tr("Supplier") to (p.supplierName ?: supplier?.name),
                tr("Supplier contact") to supplier?.let { s -> listOfNotNull(s.mobile, s.city).joinToString(", ").ifBlank { null } },
                tr("Invoice no.") to p.invoiceNo,
                tr("Supplier GSTIN") to supplier?.gstin,
                tr("Purchase date") to date(p.purchaseDate),
                tr("Status") to purchaseStatus(p.status),
                tr("Goods received") to p.receivedDate?.let { date(it) },
                tr("Items") to lines.size.toString(),
                tr("Recorded by") to ctx.name(p.createdBy),
                tr("Completed by") to ctx.name(p.completedBy),
                tr("Last changed by") to ctx.name(p.updatedBy)?.takeIf { p.updatedBy != p.createdBy },
            )),
            Table(cols, rows, totals, emptyText = tr("This purchase has no items")),
            if (profit && p.totalExpectedProfit != null) Note(PROFIT_NOTE, warning = true) else null,
            p.notes?.takeIf { it.isNotBlank() }?.let { Note(tr("Notes: %1\$s", it)) },
            if (deliveryRows.isNotEmpty()) Heading(tr("Goods received")) else null,
            if (deliveryRows.isNotEmpty()) Table(
                listOf(Column(tr("Date"), 1.2f), Column(tr("Received by"), 1.6f), Column(tr("Item"), 3.2f),
                    Column(tr("Quantity"), 1.3f, Align.RIGHT), Column(tr("Batch"), 1.3f), Column(tr("Expiry"), 1.2f)),
                deliveryRows) else null,
            if (deliveries.any { it.pending }) Note(tr("Grey rows are saved on this phone and not yet synced.")) else null,
        )
    }

    // ------------------------------------------------------------------------------------- 3. supplier report

    fun supplierReport(ctx: PdfContext, supplier: SupplierEntity, purchases: List<PurchaseEntity>,
                       from: LocalDate?, to: LocalDate?): ReportDoc {
        val profit = ctx.showProfit
        val listed = purchases.filter { it.status != "DRAFT" && !it.deleted }.sortedWith(compareBy({ it.purchaseDate }, { it.updatedAt }))
        val counted = listed.filter(::counted)
        val cols = buildList {
            add(Column(tr("Date"), 1.2f)); add(Column(tr("Invoice"), 1.6f)); add(Column(tr("Status"), 1.5f))
            add(Column(tr("Items"), 0.8f, Align.RIGHT)); add(Column(tr("Total cost"), 1.6f, Align.RIGHT))
            if (profit) add(Column(tr("Expected profit"), 1.6f, Align.RIGHT))
        }
        val rows = listed.map { p ->
            Row(buildList {
                add(date(p.purchaseDate)); add(p.invoiceNo ?: PdfFormat.NONE); add(purchaseStatus(p.status))
                add(p.itemCount.toString()); add(money(p.totalCost))
                if (profit) add(money(p.totalExpectedProfit))
            }, muted = p.status == "CANCELLED")
        }
        val totalCost = PdfFormat.sum(counted.map { it.totalCost })
        val totals = listOf(Row(buildList {
            add(tr("Total")); add(tr("%1\$s purchase(s)", counted.size)); add(""); add("")
            add(totalCost.m()); if (profit) add(PdfFormat.sum(counted.map { it.totalExpectedProfit }).m())
        }))
        return doc(ctx, tr("Supplier purchase report"), listOf(supplier.name, PdfFormat.period(from, to)),
            PdfFormat.fileName(tr("Supplier"), supplier.name, from?.toString(), to?.toString()),
            Facts(listOf(
                tr("Supplier") to supplier.name,
                tr("Contact") to listOfNotNull(supplier.contactPerson, supplier.mobile).joinToString(", ").ifBlank { null },
                tr("Address") to listOfNotNull(supplier.address, supplier.city).joinToString(", ").ifBlank { null },
                "GSTIN" to supplier.gstin,
                tr("Period") to PdfFormat.period(from, to),
                tr("Purchases") to counted.size.toString(),
                tr("Total cost") to totalCost.m(),
                tr("Average purchase") to if (counted.isEmpty()) null else totalCost.divide(BigDecimal(counted.size), 2, RoundingMode.HALF_UP).m(),
                tr("First purchase") to counted.minOfOrNull { it.purchaseDate }?.let { date(it) },
                tr("Last purchase") to counted.maxOfOrNull { it.purchaseDate }?.let { date(it) },
            )),
            Table(cols, rows, totals, emptyText = tr("No purchases from this supplier in this period")),
            if (listed.any { it.status == "CANCELLED" }) Note(tr("Cancelled purchases are listed in grey and not counted in the totals.")) else null,
            if (profit) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    // ------------------------------------------------------------------------------------- 4. item report

    fun itemReport(ctx: PdfContext, item: ItemEntity, rows: List<ItemHistoryRow>, from: LocalDate?, to: LocalDate?): ReportDoc {
        val sorted = rows.sortedBy { it.purchaseDate }
        val counted = sorted.filter { it.status != "CANCELLED" }
        val oneUnit = counted.map { it.unitId }.distinct().size <= 1
        val unit = counted.firstOrNull()?.unitCode
        val prices = counted.mapNotNull { it.purchaseUnitPrice?.toBigDecimalOrNull() }
        val priced = counted.filter { it.totalCost != null }
        val pricedQty = PdfFormat.sum(priced.map { it.quantity })
        val pricedCost = PdfFormat.sum(priced.map { it.totalCost })
        val average = if (oneUnit && pricedQty.signum() > 0) pricedCost.divide(pricedQty, 4, RoundingMode.HALF_UP) else null
        val table = Table(
            listOf(Column(tr("Date"), 1.15f), Column(tr("Supplier"), 2.3f), Column(tr("Invoice"), 1.3f), Column(tr("Quantity"), 1.3f, Align.RIGHT),
                Column(tr("Unit cost"), 1.25f, Align.RIGHT), Column(tr("Selling price"), 1.25f, Align.RIGHT), Column(tr("Amount"), 1.4f, Align.RIGHT)),
            sorted.map { r ->
                Row(listOf(date(r.purchaseDate), (r.supplierName ?: PdfFormat.NONE) + if (r.status == "CANCELLED") "\nCancelled" else "",
                    r.invoiceNo ?: PdfFormat.NONE, qty(r.quantity, r.unitCode), unitPrice(r.purchaseUnitPrice),
                    unitPrice(r.sellingPrice), money(r.totalCost)), muted = r.status == "CANCELLED")
            },
            totals = listOf(Row(listOf(tr("Total"), "${counted.map { it.purchaseId }.distinct().size} purchase(s)", "",
                if (oneUnit) qty(PdfFormat.sum(counted.map { it.quantity }).toPlainString(), unit) else tr("Mixed units"), "", "",
                PdfFormat.sum(counted.map { it.totalCost }).m()))),
            emptyText = tr("No purchases of this item in this period"),
        )
        return doc(ctx, tr("Item purchase report"), listOf(item.name, PdfFormat.period(from, to)),
            PdfFormat.fileName(tr("Item"), item.name, from?.toString(), to?.toString()),
            Facts(listOf(
                tr("Item") to item.name,
                tr("Category") to item.category,
                tr("Period") to PdfFormat.period(from, to),
                tr("Purchases") to counted.map { it.purchaseId }.distinct().size.toString(),
                tr("Lowest unit cost") to prices.minOrNull()?.let { unitPrice(it.toPlainString()) },
                tr("Highest unit cost") to prices.maxOrNull()?.let { unitPrice(it.toPlainString()) },
                tr("Average unit cost") to average?.let { tr("%1\$s (weighted)", unitPrice(it.toPlainString())) },
                tr("Latest unit cost") to counted.lastOrNull { it.purchaseUnitPrice != null }?.let { unitPrice(it.purchaseUnitPrice) },
            )),
            table,
            if (!oneUnit) Note(tr("This item was bought in more than one unit, so quantities are not added up.")) else null,
            if (sorted.any { it.status == "CANCELLED" }) Note(tr("Cancelled purchases are listed in grey and not counted in the totals.")) else null,
        )
    }

    // ------------------------------------------------------------------------------------- 5. date-range report

    fun dateRangeReport(ctx: PdfContext, purchases: List<PurchaseEntity>, from: LocalDate?, to: LocalDate?): ReportDoc {
        val profit = ctx.showProfit
        val listed = purchases.filter { it.status != "DRAFT" && !it.deleted }.sortedWith(compareBy({ it.purchaseDate }, { it.updatedAt }))
        val counted = listed.filter(::counted)
        val grand = PdfFormat.sum(counted.map { it.totalCost })
        val cols = buildList {
            add(Column(tr("Date"), 1.15f)); add(Column(tr("Supplier"), 2.4f)); add(Column(tr("Invoice"), 1.35f))
            add(Column(tr("Status"), 1.35f)); add(Column(tr("Items"), 0.7f, Align.RIGHT)); add(Column(tr("Total cost"), 1.5f, Align.RIGHT))
            if (profit) add(Column(tr("Expected profit"), 1.5f, Align.RIGHT))
        }
        val rows = listed.map { p ->
            Row(buildList {
                add(date(p.purchaseDate)); add(p.supplierName ?: PdfFormat.NONE); add(p.invoiceNo ?: PdfFormat.NONE)
                add(purchaseStatus(p.status)); add(p.itemCount.toString()); add(money(p.totalCost))
                if (profit) add(money(p.totalExpectedProfit))
            }, muted = p.status == "CANCELLED")
        }
        val totals = listOf(Row(buildList {
            add(tr("Total")); add("${counted.size} purchase(s)"); add(""); add(""); add(counted.sumOf { it.itemCount }.toString())
            add(grand.m()); if (profit) add(PdfFormat.sum(counted.map { it.totalExpectedProfit }).m())
        }))
        val bySupplier = counted.groupBy { it.supplierId }.map { (_, ps) ->
            val cost = PdfFormat.sum(ps.map { it.totalCost })
            Triple(ps.first().supplierName ?: PdfFormat.NONE, ps.size, cost)
        }.sortedWith(compareByDescending<Triple<String, Int, BigDecimal>> { it.third }.thenBy { it.first.lowercase() })
        return doc(ctx, tr("Purchase report"), listOf(PdfFormat.period(from, to)),
            PdfFormat.fileName(tr("Purchases"), from?.toString(), to?.toString()),
            Facts(listOf(
                tr("Period") to PdfFormat.period(from, to),
                tr("Purchases") to counted.size.toString(),
                tr("Suppliers") to bySupplier.size.toString(),
                tr("Total cost") to grand.m(),
                tr("Cancelled") to listed.count { it.status == "CANCELLED" }.takeIf { it > 0 }?.toString(),
                tr("Expected profit") to if (profit) PdfFormat.sum(counted.map { it.totalExpectedProfit }).m() else null,
            )),
            Table(cols, rows, totals, emptyText = tr("No purchases in this period")),
            Heading(tr("Totals by supplier")),
            Table(
                listOf(Column(tr("Supplier"), 3.5f), Column(tr("Purchases"), 1.2f, Align.RIGHT), Column(tr("Total cost"), 1.8f, Align.RIGHT),
                    Column(tr("Share"), 1.1f, Align.RIGHT)),
                bySupplier.map { (name, n, cost) -> Row(listOf(name, n.toString(), cost.m(), share(cost, grand))) },
                totals = listOf(Row(listOf(tr("Total"), counted.size.toString(), grand.m(), if (grand.signum() > 0) "100.00%" else PdfFormat.NONE))),
                emptyText = tr("No purchases in this period"),
            ),
            if (listed.any { it.status == "CANCELLED" }) Note(tr("Cancelled purchases are listed in grey and not counted in the totals.")) else null,
            if (profit) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    private fun share(part: BigDecimal, whole: BigDecimal): String =
        if (whole.signum() == 0) PdfFormat.NONE
        else PdfFormat.percent(part.multiply(BigDecimal(100)).divide(whole, 2, RoundingMode.HALF_UP).toPlainString())

    // ------------------------------------------------------------------------------------- 6. expiry

    fun expiryReport(ctx: PdfContext, batches: List<BatchEntity>, filter: ExpiryCalculator.Filter, today: LocalDate): ReportDoc {
        val rows = batches.map { b ->
            val exp = b.expiryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            Row(listOf(b.itemName, b.batchNo ?: PdfFormat.NONE, date(b.expiryDate), ExpiryCalculator.label(exp, today),
                qty(b.quantity, b.unitCode), b.supplierName ?: PdfFormat.NONE,
                listOfNotNull(b.invoiceNo, date(b.purchaseDate)).joinToString("\n")),
                bold = exp != null && exp.isBefore(today))
        }
        val expired = batches.count { b -> b.expiryDate?.let { LocalDate.parse(it).isBefore(today) } == true }
        return doc(ctx, tr("Expiry report"), listOf(trMessage(filter.label), tr("As on %1\$s", date(today))),
            PdfFormat.fileName(tr("Expiry"), filter.name.lowercase(), today.toString()),
            Facts(listOf(
                tr("List") to trMessage(filter.label),
                tr("As on") to date(today),
                tr("Batches") to batches.size.toString(),
                tr("Already expired") to expired.toString(),
            )),
            Table(
                listOf(Column(tr("Item"), 2.8f), Column(tr("Batch"), 1.2f), Column(tr("Expiry"), 1.1f), Column(tr("Status"), 1.5f),
                    Column(tr("Stock"), 1.2f, Align.RIGHT), Column(tr("Supplier"), 1.7f), Column(tr("Invoice / date"), 1.4f)),
                rows, emptyText = tr("No batches in this list"),
            ),
            Note(tr("Only stock still on hand from completed purchases is listed. Expired batches are in bold.")),
        )
    }

    // ------------------------------------------------------------------------------------- server reports

    fun purchaseReport(ctx: PdfContext, r: PurchaseReportDto): ReportDoc {
        val profit = r.profitIncluded
        val cols = buildList {
            add(Column(tr("Date"), 1.15f)); add(Column(tr("Supplier"), 2.4f)); add(Column(tr("Invoice"), 1.35f))
            add(Column(tr("Status"), 1.35f)); add(Column(tr("Items"), 0.7f, Align.RIGHT)); add(Column(tr("Total cost"), 1.5f, Align.RIGHT))
            if (profit) add(Column(tr("Expected profit"), 1.5f, Align.RIGHT))
        }
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        return doc(ctx, tr("Purchase report"), listOf(period), PdfFormat.fileName(tr("Purchases"), r.period.from, r.period.to),
            Facts(listOf(tr("Period") to period, tr("Purchases") to r.purchaseCount.toString(), tr("Total cost") to money(r.totalCost),
                tr("Cancelled") to r.cancelledCount.takeIf { it > 0 }?.toString(),
                tr("Expected profit") to if (profit) money(r.expectedProfit) else null)),
            Table(cols, r.rows.map { p ->
                Row(buildList {
                    add(date(p.purchaseDate)); add(p.supplierName ?: PdfFormat.NONE); add(p.invoiceNo ?: PdfFormat.NONE)
                    add(purchaseStatus(p.status)); add(p.itemCount.toString()); add(money(p.totalCost))
                    if (profit) add(money(p.expectedProfit))
                }, muted = p.status == "CANCELLED")
            }, listOf(Row(buildList {
                add(tr("Total")); add("${r.purchaseCount} purchase(s)"); add(""); add(""); add(""); add(money(r.totalCost))
                if (profit) add(money(r.expectedProfit))
            })), emptyText = tr("No purchases in this period")),
            if (r.cancelledCount > 0) Note(tr("Cancelled purchases are listed in grey and not counted in the totals.")) else null,
            if (profit) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    fun supplierTotals(ctx: PdfContext, r: SupplierTotalsDto): ReportDoc {
        val profit = r.profitIncluded
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        val cols = buildList {
            add(Column(tr("Supplier"), 3f)); add(Column(tr("Purchases"), 1f, Align.RIGHT)); add(Column(tr("Total cost"), 1.6f, Align.RIGHT))
            add(Column(tr("Share"), 1f, Align.RIGHT)); add(Column(tr("Last purchase"), 1.3f))
            if (profit) add(Column(tr("Expected profit"), 1.5f, Align.RIGHT))
        }
        return doc(ctx, tr("Supplier totals"), listOf(period), PdfFormat.fileName(tr("Supplier-totals"), r.period.from, r.period.to),
            Facts(listOf(tr("Period") to period, tr("Suppliers") to r.rows.size.toString(), tr("Purchases") to r.purchaseCount.toString(),
                tr("Total cost") to money(r.totalCost))),
            Table(cols, r.rows.map { s ->
                Row(buildList {
                    add(s.supplierName ?: PdfFormat.NONE); add(s.purchaseCount.toString()); add(money(s.totalCost))
                    add(PdfFormat.percent(s.sharePercent)); add(date(s.lastPurchaseDate))
                    if (profit) add(money(s.expectedProfit))
                })
            }, listOf(Row(buildList {
                add(tr("Total")); add(r.purchaseCount.toString()); add(money(r.totalCost)); add(if (r.rows.isEmpty()) PdfFormat.NONE else "100.00%"); add("")
                if (profit) add(PdfFormat.sum(r.rows.map { it.expectedProfit }).m())
            })), emptyText = tr("No purchases in this period")),
            if (profit) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    fun itemPrices(ctx: PdfContext, r: ItemPricesDto): ReportDoc {
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        val cols = buildList {
            add(Column(tr("Item"), 2.6f)); add(Column(tr("Bought"), 1.3f, Align.RIGHT)); add(Column(tr("Lowest"), 1.1f, Align.RIGHT))
            add(Column(tr("Highest"), 1.1f, Align.RIGHT)); add(Column(tr("Average"), 1.1f, Align.RIGHT))
            add(Column(tr("Latest"), 1.9f, Align.RIGHT)); add(Column(tr("Selling price"), 1.1f, Align.RIGHT))
            if (r.profitIncluded) add(Column(tr("Expected profit"), 1.3f, Align.RIGHT))
        }
        return doc(ctx, tr("Item purchase prices"), listOf(period), PdfFormat.fileName(tr("Item-prices"), r.period.from, r.period.to),
            Facts(listOf(tr("Period") to period, tr("Items") to r.rows.size.toString())),
            Table(cols, r.rows.map { i ->
                Row(buildList {
                    add(i.itemName); add(qty(i.totalQuantity, i.unitCode) + tr("\n%1\$s purchase(s)", i.purchaseCount))
                    add(unitPrice(i.lowestUnitPrice)); add(unitPrice(i.highestUnitPrice)); add(unitPrice(i.averageUnitPrice))
                    add(unitPrice(i.latestUnitPrice) + "\n" + listOfNotNull(date(i.latestDate), i.latestSupplierName).joinToString(", "))
                    add(unitPrice(i.latestSellingPrice))
                    if (r.profitIncluded) add(money(i.expectedProfit))
                })
            }, emptyText = tr("No purchases in this period")),
            Note(tr("Prices are per unit. The average is weighted by quantity. An item bought in two units appears once per unit.")),
            if (r.profitIncluded) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    fun expectedProfit(ctx: PdfContext, r: ExpectedProfitReportDto): ReportDoc {
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        val cols = { first: String ->
            listOf(Column(first, 3f), Column(tr("Cost"), 1.4f, Align.RIGHT), Column(tr("Expected sales"), 1.5f, Align.RIGHT),
                Column(tr("Expected profit"), 1.5f, Align.RIGHT), Column(tr("Expected margin"), 1.2f, Align.RIGHT))
        }
        fun rows(list: List<app.stocklist.app.data.remote.ProfitRowDto>) = list.map {
            Row(listOf(it.name ?: PdfFormat.NONE, money(it.cost), money(it.expectedSales), money(it.expectedProfit),
                PdfFormat.percent(it.marginPercent)))
        }
        return doc(ctx, tr("Expected profit report"), listOf(period, tr("Estimate only, not realised profit")),
            PdfFormat.fileName(tr("Expected-profit"), r.period.from, r.period.to),
            Note(r.note.ifBlank { PROFIT_NOTE }, warning = true),
            Facts(listOf(
                tr("Period") to period, tr("Purchases") to r.purchaseCount.toString(),
                tr("Total cost") to money(r.totalCost), tr("Cost with selling price") to money(r.pricedCost),
                tr("Cost without selling price") to money(r.unpricedCost), tr("Expected sales") to money(r.expectedSales),
                tr("Expected profit") to money(r.expectedProfit), tr("Expected margin") to PdfFormat.percent(r.marginPercent),
            )),
            Heading(tr("By supplier")),
            Table(cols(tr("Supplier")), rows(r.bySupplier), emptyText = tr("No purchases in this period")),
            Heading(tr("By item")),
            Table(cols(tr("Item")), rows(r.byItem), emptyText = tr("No purchases in this period")),
        )
    }
}
