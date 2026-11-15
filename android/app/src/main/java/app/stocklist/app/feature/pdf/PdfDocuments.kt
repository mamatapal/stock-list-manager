package app.stocklist.app.feature.pdf

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
    const val PROFIT_NOTE = "Expected profit is an estimate: selling price minus purchase cost, for lines that have a " +
        "selling price. It is not realised profit; actual sales are not recorded in this app."

    fun purchaseStatus(s: String): String = when (s) {
        "DRAFT" -> "Draft"
        "PENDING" -> "Awaiting goods"
        "PARTIAL" -> "Partly received"
        "RECEIVED" -> "Received"
        "CANCELLED" -> "Cancelled"
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
                l.priceBasis == "TOTAL" -> unitPrice(l.enteredPrice) + " total"
                else -> unitPrice(l.enteredPrice) + (unit?.let { " / $it" } ?: "")
            }
            Row(listOf((i + 1).toString(), l.itemName + (l.description?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
                qty(l.quantity, unit), price, money(l.lineTotal)))
        }
        val total = list.totalAmount ?: PdfFormat.sum(lines.map { it.lineTotal }).toPlainString()
        return doc(ctx, "Out-of-stock list", listOf(list.title), PdfFormat.fileName("List", list.title, list.listDate),
            Facts(listOf(
                "List date" to date(list.listDate),
                "Supplier" to supplier?.let { s -> listOfNotNull(s.name, s.mobile, s.city).joinToString(", ") },
                "Status" to label(list.status),
                "Items" to lines.size.toString(),
                "Prepared by" to ctx.name(list.createdBy),
            )),
            list.notes?.takeIf { it.isNotBlank() }?.let { Note("Notes: $it") },
            Table(
                listOf(Column("#", 0.5f, Align.RIGHT), Column("Item", 4.6f), Column("Quantity", 1.5f, Align.RIGHT),
                    Column("Price", 1.7f, Align.RIGHT), Column("Amount", 1.6f, Align.RIGHT)),
                rows,
                totals = listOf(Row(listOf("", "Total (${lines.size} item${if (lines.size == 1) "" else "s"})", "", "", money(total)))),
                emptyText = "This list has no items",
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
            add(Column("#", 0.45f, Align.RIGHT)); add(Column("Item", 3.4f)); add(Column("Quantity", 1.35f, Align.RIGHT))
            add(Column("Received", 1.2f, Align.RIGHT)); add(Column("Unit cost", 1.3f, Align.RIGHT))
            add(Column("Amount", 1.45f, Align.RIGHT))
            if (profit) { add(Column("Selling price", 1.3f, Align.RIGHT)); add(Column("Expected profit", 1.4f, Align.RIGHT)) }
        }
        val rows = lines.sortedBy { it.sortOrder }.mapIndexed { i, l ->
            Row(buildList {
                add((i + 1).toString()); add(l.itemName); add(qty(l.quantity, l.unitCode))
                add(qty(received[l.id]?.toPlainString() ?: "0")); add(unitPrice(l.purchaseUnitPrice)); add(money(l.totalCost))
                if (profit) { add(unitPrice(l.sellingPrice)); add(money(l.expectedProfit)) }
            })
        }
        val totals = listOf(Row(buildList {
            add(""); add("Total"); add(""); add(""); add(""); add(money(p.totalCost))
            if (profit) { add(""); add(money(p.totalExpectedProfit)) }
        }))
        val byDelivery = deliveryLines.groupBy { it.deliveryId }
        val lineName = lines.associate { it.id to (it.itemName to it.unitCode) }
        val deliveryRows = deliveries.sortedWith(compareBy({ it.receivedDate }, { it.createdAt })).flatMap { d ->
            byDelivery[d.id].orEmpty().map { dl ->
                val (name, unit) = lineName[dl.purchaseLineId] ?: ("Item" to null)
                val correction = dl.kind == "CORRECTION"
                Row(listOf(date(d.receivedDate), ctx.name(d.receivedBy) ?: PdfFormat.NONE,
                    name + (if (correction) "\nCorrection" + (dl.reason?.let { ": $it" } ?: "") else ""),
                    qty(dl.quantity, unit), dl.batchNo ?: PdfFormat.NONE, date(dl.expiryDate)),
                    muted = d.pending)
            }
        }
        val cancelled = p.status == "CANCELLED"
        return doc(ctx, "Purchase", listOfNotNull(
            listOfNotNull(p.supplierName ?: supplier?.name, p.invoiceNo?.let { "Invoice $it" }).joinToString("  ·  ")),
            PdfFormat.fileName("Purchase", p.invoiceNo ?: p.supplierName, p.purchaseDate),
            if (cancelled) Note("CANCELLED. This purchase is not counted in any total." +
                (p.cancelReason?.let { " Reason: $it" } ?: ""), warning = true) else null,
            Facts(listOf(
                "Supplier" to (p.supplierName ?: supplier?.name),
                "Supplier contact" to supplier?.let { s -> listOfNotNull(s.mobile, s.city).joinToString(", ").ifBlank { null } },
                "Invoice no." to p.invoiceNo,
                "Supplier GSTIN" to supplier?.gstin,
                "Purchase date" to date(p.purchaseDate),
                "Status" to purchaseStatus(p.status),
                "Goods received" to p.receivedDate?.let { date(it) },
                "Items" to lines.size.toString(),
                "Recorded by" to ctx.name(p.createdBy),
                "Completed by" to ctx.name(p.completedBy),
                "Last changed by" to ctx.name(p.updatedBy)?.takeIf { p.updatedBy != p.createdBy },
            )),
            Table(cols, rows, totals, emptyText = "This purchase has no items"),
            if (profit && p.totalExpectedProfit != null) Note(PROFIT_NOTE, warning = true) else null,
            p.notes?.takeIf { it.isNotBlank() }?.let { Note("Notes: $it") },
            if (deliveryRows.isNotEmpty()) Heading("Goods received") else null,
            if (deliveryRows.isNotEmpty()) Table(
                listOf(Column("Date", 1.2f), Column("Received by", 1.6f), Column("Item", 3.2f),
                    Column("Quantity", 1.3f, Align.RIGHT), Column("Batch", 1.3f), Column("Expiry", 1.2f)),
                deliveryRows) else null,
            if (deliveries.any { it.pending }) Note("Grey rows are saved on this phone and not yet synced.") else null,
        )
    }

    // ------------------------------------------------------------------------------------- 3. supplier report

    fun supplierReport(ctx: PdfContext, supplier: SupplierEntity, purchases: List<PurchaseEntity>,
                       from: LocalDate?, to: LocalDate?): ReportDoc {
        val profit = ctx.showProfit
        val listed = purchases.filter { it.status != "DRAFT" && !it.deleted }.sortedWith(compareBy({ it.purchaseDate }, { it.updatedAt }))
        val counted = listed.filter(::counted)
        val cols = buildList {
            add(Column("Date", 1.2f)); add(Column("Invoice", 1.6f)); add(Column("Status", 1.5f))
            add(Column("Items", 0.8f, Align.RIGHT)); add(Column("Total cost", 1.6f, Align.RIGHT))
            if (profit) add(Column("Expected profit", 1.6f, Align.RIGHT))
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
            add("Total"); add("${counted.size} purchase${if (counted.size == 1) "" else "s"}"); add(""); add("")
            add(totalCost.m()); if (profit) add(PdfFormat.sum(counted.map { it.totalExpectedProfit }).m())
        }))
        return doc(ctx, "Supplier purchase report", listOf(supplier.name, PdfFormat.period(from, to)),
            PdfFormat.fileName("Supplier", supplier.name, from?.toString(), to?.toString()),
            Facts(listOf(
                "Supplier" to supplier.name,
                "Contact" to listOfNotNull(supplier.contactPerson, supplier.mobile).joinToString(", ").ifBlank { null },
                "Address" to listOfNotNull(supplier.address, supplier.city).joinToString(", ").ifBlank { null },
                "GSTIN" to supplier.gstin,
                "Period" to PdfFormat.period(from, to),
                "Purchases" to counted.size.toString(),
                "Total cost" to totalCost.m(),
                "Average purchase" to if (counted.isEmpty()) null else totalCost.divide(BigDecimal(counted.size), 2, RoundingMode.HALF_UP).m(),
                "First purchase" to counted.minOfOrNull { it.purchaseDate }?.let { date(it) },
                "Last purchase" to counted.maxOfOrNull { it.purchaseDate }?.let { date(it) },
            )),
            Table(cols, rows, totals, emptyText = "No purchases from this supplier in this period"),
            if (listed.any { it.status == "CANCELLED" }) Note("Cancelled purchases are listed in grey and not counted in the totals.") else null,
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
            listOf(Column("Date", 1.15f), Column("Supplier", 2.3f), Column("Invoice", 1.3f), Column("Quantity", 1.3f, Align.RIGHT),
                Column("Unit cost", 1.25f, Align.RIGHT), Column("Selling price", 1.25f, Align.RIGHT), Column("Amount", 1.4f, Align.RIGHT)),
            sorted.map { r ->
                Row(listOf(date(r.purchaseDate), (r.supplierName ?: PdfFormat.NONE) + if (r.status == "CANCELLED") "\nCancelled" else "",
                    r.invoiceNo ?: PdfFormat.NONE, qty(r.quantity, r.unitCode), unitPrice(r.purchaseUnitPrice),
                    unitPrice(r.sellingPrice), money(r.totalCost)), muted = r.status == "CANCELLED")
            },
            totals = listOf(Row(listOf("Total", "${counted.map { it.purchaseId }.distinct().size} purchase(s)", "",
                if (oneUnit) qty(PdfFormat.sum(counted.map { it.quantity }).toPlainString(), unit) else "Mixed units", "", "",
                PdfFormat.sum(counted.map { it.totalCost }).m()))),
            emptyText = "No purchases of this item in this period",
        )
        return doc(ctx, "Item purchase report", listOf(item.name, PdfFormat.period(from, to)),
            PdfFormat.fileName("Item", item.name, from?.toString(), to?.toString()),
            Facts(listOf(
                "Item" to item.name,
                "Category" to item.category,
                "Period" to PdfFormat.period(from, to),
                "Purchases" to counted.map { it.purchaseId }.distinct().size.toString(),
                "Lowest unit cost" to prices.minOrNull()?.let { unitPrice(it.toPlainString()) },
                "Highest unit cost" to prices.maxOrNull()?.let { unitPrice(it.toPlainString()) },
                "Average unit cost" to average?.let { unitPrice(it.toPlainString()) + " (weighted)" },
                "Latest unit cost" to counted.lastOrNull { it.purchaseUnitPrice != null }?.let { unitPrice(it.purchaseUnitPrice) },
            )),
            table,
            if (!oneUnit) Note("This item was bought in more than one unit, so quantities are not added up.") else null,
            if (sorted.any { it.status == "CANCELLED" }) Note("Cancelled purchases are listed in grey and not counted in the totals.") else null,
        )
    }

    // ------------------------------------------------------------------------------------- 5. date-range report

    fun dateRangeReport(ctx: PdfContext, purchases: List<PurchaseEntity>, from: LocalDate?, to: LocalDate?): ReportDoc {
        val profit = ctx.showProfit
        val listed = purchases.filter { it.status != "DRAFT" && !it.deleted }.sortedWith(compareBy({ it.purchaseDate }, { it.updatedAt }))
        val counted = listed.filter(::counted)
        val grand = PdfFormat.sum(counted.map { it.totalCost })
        val cols = buildList {
            add(Column("Date", 1.15f)); add(Column("Supplier", 2.4f)); add(Column("Invoice", 1.35f))
            add(Column("Status", 1.35f)); add(Column("Items", 0.7f, Align.RIGHT)); add(Column("Total cost", 1.5f, Align.RIGHT))
            if (profit) add(Column("Expected profit", 1.5f, Align.RIGHT))
        }
        val rows = listed.map { p ->
            Row(buildList {
                add(date(p.purchaseDate)); add(p.supplierName ?: PdfFormat.NONE); add(p.invoiceNo ?: PdfFormat.NONE)
                add(purchaseStatus(p.status)); add(p.itemCount.toString()); add(money(p.totalCost))
                if (profit) add(money(p.totalExpectedProfit))
            }, muted = p.status == "CANCELLED")
        }
        val totals = listOf(Row(buildList {
            add("Total"); add("${counted.size} purchase(s)"); add(""); add(""); add(counted.sumOf { it.itemCount }.toString())
            add(grand.m()); if (profit) add(PdfFormat.sum(counted.map { it.totalExpectedProfit }).m())
        }))
        val bySupplier = counted.groupBy { it.supplierId }.map { (_, ps) ->
            val cost = PdfFormat.sum(ps.map { it.totalCost })
            Triple(ps.first().supplierName ?: PdfFormat.NONE, ps.size, cost)
        }.sortedWith(compareByDescending<Triple<String, Int, BigDecimal>> { it.third }.thenBy { it.first.lowercase() })
        return doc(ctx, "Purchase report", listOf(PdfFormat.period(from, to)),
            PdfFormat.fileName("Purchases", from?.toString(), to?.toString()),
            Facts(listOf(
                "Period" to PdfFormat.period(from, to),
                "Purchases" to counted.size.toString(),
                "Suppliers" to bySupplier.size.toString(),
                "Total cost" to grand.m(),
                "Cancelled" to listed.count { it.status == "CANCELLED" }.takeIf { it > 0 }?.toString(),
                "Expected profit" to if (profit) PdfFormat.sum(counted.map { it.totalExpectedProfit }).m() else null,
            )),
            Table(cols, rows, totals, emptyText = "No purchases in this period"),
            Heading("Totals by supplier"),
            Table(
                listOf(Column("Supplier", 3.5f), Column("Purchases", 1.2f, Align.RIGHT), Column("Total cost", 1.8f, Align.RIGHT),
                    Column("Share", 1.1f, Align.RIGHT)),
                bySupplier.map { (name, n, cost) -> Row(listOf(name, n.toString(), cost.m(), share(cost, grand))) },
                totals = listOf(Row(listOf("Total", counted.size.toString(), grand.m(), if (grand.signum() > 0) "100.00%" else PdfFormat.NONE))),
                emptyText = "No purchases in this period",
            ),
            if (listed.any { it.status == "CANCELLED" }) Note("Cancelled purchases are listed in grey and not counted in the totals.") else null,
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
        return doc(ctx, "Expiry report", listOf(filter.label, "As on ${date(today)}"),
            PdfFormat.fileName("Expiry", filter.name.lowercase(), today.toString()),
            Facts(listOf(
                "List" to filter.label,
                "As on" to date(today),
                "Batches" to batches.size.toString(),
                "Already expired" to expired.toString(),
            )),
            Table(
                listOf(Column("Item", 2.8f), Column("Batch", 1.2f), Column("Expiry", 1.1f), Column("Status", 1.5f),
                    Column("Stock", 1.2f, Align.RIGHT), Column("Supplier", 1.7f), Column("Invoice / date", 1.4f)),
                rows, emptyText = "No batches in this list",
            ),
            Note("Only stock still on hand from completed purchases is listed. Expired batches are in bold."),
        )
    }

    // ------------------------------------------------------------------------------------- server reports

    fun purchaseReport(ctx: PdfContext, r: PurchaseReportDto): ReportDoc {
        val profit = r.profitIncluded
        val cols = buildList {
            add(Column("Date", 1.15f)); add(Column("Supplier", 2.4f)); add(Column("Invoice", 1.35f))
            add(Column("Status", 1.35f)); add(Column("Items", 0.7f, Align.RIGHT)); add(Column("Total cost", 1.5f, Align.RIGHT))
            if (profit) add(Column("Expected profit", 1.5f, Align.RIGHT))
        }
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        return doc(ctx, "Purchase report", listOf(period), PdfFormat.fileName("Purchases", r.period.from, r.period.to),
            Facts(listOf("Period" to period, "Purchases" to r.purchaseCount.toString(), "Total cost" to money(r.totalCost),
                "Cancelled" to r.cancelledCount.takeIf { it > 0 }?.toString(),
                "Expected profit" to if (profit) money(r.expectedProfit) else null)),
            Table(cols, r.rows.map { p ->
                Row(buildList {
                    add(date(p.purchaseDate)); add(p.supplierName ?: PdfFormat.NONE); add(p.invoiceNo ?: PdfFormat.NONE)
                    add(purchaseStatus(p.status)); add(p.itemCount.toString()); add(money(p.totalCost))
                    if (profit) add(money(p.expectedProfit))
                }, muted = p.status == "CANCELLED")
            }, listOf(Row(buildList {
                add("Total"); add("${r.purchaseCount} purchase(s)"); add(""); add(""); add(""); add(money(r.totalCost))
                if (profit) add(money(r.expectedProfit))
            })), emptyText = "No purchases in this period"),
            if (r.cancelledCount > 0) Note("Cancelled purchases are listed in grey and not counted in the totals.") else null,
            if (profit) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    fun supplierTotals(ctx: PdfContext, r: SupplierTotalsDto): ReportDoc {
        val profit = r.profitIncluded
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        val cols = buildList {
            add(Column("Supplier", 3f)); add(Column("Purchases", 1f, Align.RIGHT)); add(Column("Total cost", 1.6f, Align.RIGHT))
            add(Column("Share", 1f, Align.RIGHT)); add(Column("Last purchase", 1.3f))
            if (profit) add(Column("Expected profit", 1.5f, Align.RIGHT))
        }
        return doc(ctx, "Supplier totals", listOf(period), PdfFormat.fileName("Supplier-totals", r.period.from, r.period.to),
            Facts(listOf("Period" to period, "Suppliers" to r.rows.size.toString(), "Purchases" to r.purchaseCount.toString(),
                "Total cost" to money(r.totalCost))),
            Table(cols, r.rows.map { s ->
                Row(buildList {
                    add(s.supplierName ?: PdfFormat.NONE); add(s.purchaseCount.toString()); add(money(s.totalCost))
                    add(PdfFormat.percent(s.sharePercent)); add(date(s.lastPurchaseDate))
                    if (profit) add(money(s.expectedProfit))
                })
            }, listOf(Row(buildList {
                add("Total"); add(r.purchaseCount.toString()); add(money(r.totalCost)); add(if (r.rows.isEmpty()) PdfFormat.NONE else "100.00%"); add("")
                if (profit) add(PdfFormat.sum(r.rows.map { it.expectedProfit }).m())
            })), emptyText = "No purchases in this period"),
            if (profit) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    fun itemPrices(ctx: PdfContext, r: ItemPricesDto): ReportDoc {
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        val cols = buildList {
            add(Column("Item", 2.6f)); add(Column("Bought", 1.3f, Align.RIGHT)); add(Column("Lowest", 1.1f, Align.RIGHT))
            add(Column("Highest", 1.1f, Align.RIGHT)); add(Column("Average", 1.1f, Align.RIGHT))
            add(Column("Latest", 1.9f, Align.RIGHT)); add(Column("Selling price", 1.1f, Align.RIGHT))
            if (r.profitIncluded) add(Column("Expected profit", 1.3f, Align.RIGHT))
        }
        return doc(ctx, "Item purchase prices", listOf(period), PdfFormat.fileName("Item-prices", r.period.from, r.period.to),
            Facts(listOf("Period" to period, "Items" to r.rows.size.toString())),
            Table(cols, r.rows.map { i ->
                Row(buildList {
                    add(i.itemName); add(qty(i.totalQuantity, i.unitCode) + "\n${i.purchaseCount} purchase(s)")
                    add(unitPrice(i.lowestUnitPrice)); add(unitPrice(i.highestUnitPrice)); add(unitPrice(i.averageUnitPrice))
                    add(unitPrice(i.latestUnitPrice) + "\n" + listOfNotNull(date(i.latestDate), i.latestSupplierName).joinToString(", "))
                    add(unitPrice(i.latestSellingPrice))
                    if (r.profitIncluded) add(money(i.expectedProfit))
                })
            }, emptyText = "No purchases in this period"),
            Note("Prices are per unit. The average is weighted by quantity. An item bought in two units appears once per unit."),
            if (r.profitIncluded) Note(PROFIT_NOTE, warning = true) else null,
        )
    }

    fun expectedProfit(ctx: PdfContext, r: ExpectedProfitReportDto): ReportDoc {
        val period = PdfFormat.period(r.period.from?.let(LocalDate::parse), r.period.to?.let(LocalDate::parse))
        val cols = { first: String ->
            listOf(Column(first, 3f), Column("Cost", 1.4f, Align.RIGHT), Column("Expected sales", 1.5f, Align.RIGHT),
                Column("Expected profit", 1.5f, Align.RIGHT), Column("Expected margin", 1.2f, Align.RIGHT))
        }
        fun rows(list: List<app.stocklist.app.data.remote.ProfitRowDto>) = list.map {
            Row(listOf(it.name ?: PdfFormat.NONE, money(it.cost), money(it.expectedSales), money(it.expectedProfit),
                PdfFormat.percent(it.marginPercent)))
        }
        return doc(ctx, "Expected profit report", listOf(period, "Estimate only, not realised profit"),
            PdfFormat.fileName("Expected-profit", r.period.from, r.period.to),
            Note(r.note.ifBlank { PROFIT_NOTE }, warning = true),
            Facts(listOf(
                "Period" to period, "Purchases" to r.purchaseCount.toString(),
                "Total cost" to money(r.totalCost), "Cost with selling price" to money(r.pricedCost),
                "Cost without selling price" to money(r.unpricedCost), "Expected sales" to money(r.expectedSales),
                "Expected profit" to money(r.expectedProfit), "Expected margin" to PdfFormat.percent(r.marginPercent),
            )),
            Heading("By supplier"),
            Table(cols("Supplier"), rows(r.bySupplier), emptyText = "No purchases in this period"),
            Heading("By item"),
            Table(cols("Item"), rows(r.byItem), emptyText = "No purchases in this period"),
        )
    }
}
