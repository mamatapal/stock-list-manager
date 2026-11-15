package app.stocklist.app

import app.stocklist.app.core.pdf.BusinessInfo
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
import app.stocklist.app.feature.pdf.PdfContext

/** Test data for PDFs: a shop, its people, suppliers, purchases and batches. */
object PdfFixtures {
    const val LONG_NAME = "Extra virgin cold-pressed groundnut oil, family pack with free steel dispenser and " +
        "measuring cup, imported batch for the festive season, not for resale outside Maharashtra"

    val business = BusinessInfo("Sharma General Store", listOf("12 MG Road", "Pune, Maharashtra 411001", "Phone 9800000000"))

    fun ctx(profit: Boolean = true) = PdfContext(business, "Asha Sharma", 1_794_700_000_000L,
        mapOf("u1" to "Asha Sharma", "u2" to "Ravi Kumar"), showProfit = profit)

    fun supplier(id: String = "s1", name: String = "Kumar Traders") = SupplierEntity(id, name, "Ramesh", "9876543210", null,
        "Market Yard", "Pune", "27ABCDE1234F1Z5", null, true, false, 1, "SYNCED", null, 0)

    fun item(id: String = "i1", name: String = "Rice") = ItemEntity(id, name, "Grains", "u-kg", null, null, null, null, null,
        true, false, 1, "SYNCED", null, 0)

    fun purchase(id: String, invoice: String?, date: String, status: String = "RECEIVED", cost: String? = "1000.00",
                 profit: String? = "200.00", supplierId: String = "s1", supplierName: String = "Kumar Traders", items: Int = 1) =
        PurchaseEntity(id = id, supplierId = supplierId, supplierName = supplierName, invoiceNo = invoice, purchaseDate = date,
            receivedDate = date, stockListId = null, notes = null, status = status, itemCount = items, totalCost = cost,
            totalExpectedSales = profit?.let { cost?.toBigDecimal()?.add(it.toBigDecimal())?.toPlainString() },
            totalExpectedProfit = profit, marginPercent = null, markupPercent = null,
            cancelReason = if (status == "CANCELLED") "Entered twice" else null, createdBy = "u2", serverVersion = 1,
            syncState = "SYNCED", updatedAt = 0, completedBy = "u2")

    fun line(id: String, purchaseId: String, name: String, qty: String, unit: String, unitPrice: String?, cost: String?,
             selling: String? = null, profit: String? = null, order: Int = 0) =
        PurchaseLineEntity(id, purchaseId, "item-$id", name, "u-$unit", unit, qty, "PER_UNIT", unitPrice, unitPrice, selling, cost,
            null, profit, null, null, null, true, order)

    fun list(lines: Int = 2) = StockListEntity(id = "l1", title = "Monday order", listDate = "2026-11-14", supplierId = "s1",
        notes = "Deliver before 10 am", status = "SAVED", receivingStatus = "PENDING", totalAmount = null, createdBy = "u1",
        serverVersion = 1, syncState = "SYNCED", updatedAt = 0)

    fun listLine(i: Int, name: String, qty: String, price: String?, total: String?, description: String? = null) =
        StockListLineEntity(id = "ll$i", listId = "l1", itemId = null, itemName = name, description = description, quantity = qty,
            unitId = "u-kg", priceBasis = "PER_UNIT", enteredPrice = price, lineTotal = total, sortOrder = i)

    fun delivery(id: String, purchaseId: String, date: String, pending: Boolean = false) =
        DeliveryEntity(id, purchaseId, date, "u2", null, pending, 0)

    fun deliveryLine(id: String, deliveryId: String, purchaseId: String, lineId: String, qty: String, batch: String? = null,
                     expiry: String? = null, kind: String = "RECEIPT", reason: String? = null) =
        DeliveryLineEntity(id, deliveryId, purchaseId, lineId, kind, qty, null, reason, batch, expiry, false)

    fun batch(id: String, item: String, expiry: String?, qty: String = "10") = BatchEntity(id, "i-$id", item, "p1", "pl1", "d1",
        "s1", "Kumar Traders", "K-1", "2026-10-01", "2026-10-02", "u2", "B-$id", qty, qty, "pcs", expiry, false, false)

    fun history(purchaseId: String, date: String, qty: String, unitPrice: String, cost: String, status: String = "RECEIVED",
                unitId: String = "u-kg", unit: String = "kg", supplier: String = "Kumar Traders") =
        ItemHistoryRow(purchaseId, "pl-$purchaseId", date, status, "s1", supplier, "INV-$purchaseId", "Rice", qty, unitId, unit,
            "PER_UNIT", unitPrice, unitPrice, null, cost)
}
