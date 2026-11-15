package app.stocklist.app.data.sync

import app.stocklist.app.data.local.BatchEntity
import app.stocklist.app.data.local.DeliveryEntity
import app.stocklist.app.data.local.PersonEntity
import app.stocklist.app.data.local.DeliveryLineEntity
import app.stocklist.app.data.local.ItemEntity
import app.stocklist.app.data.local.PurchaseEntity
import app.stocklist.app.data.local.PurchaseLineEntity
import app.stocklist.app.data.local.StockListEntity
import app.stocklist.app.data.local.StockListLineEntity
import app.stocklist.app.data.local.SupplierEntity
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.local.UnitEntity
import app.stocklist.app.data.remote.BatchDto
import app.stocklist.app.data.remote.DeliveryInput
import app.stocklist.app.data.remote.PersonDto
import app.stocklist.app.data.remote.ItemDto
import app.stocklist.app.data.remote.PurchaseDto
import app.stocklist.app.data.remote.PurchaseInput
import app.stocklist.app.data.remote.PurchaseLineInput
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.LineInput
import app.stocklist.app.data.remote.StockListDto
import app.stocklist.app.data.remote.StockListInput
import app.stocklist.app.data.remote.SupplierDto
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.remote.UnitDto

object EntityTypes {
    const val SUPPLIER = "SUPPLIER"
    const val ITEM = "ITEM"
    const val UNIT = "UNIT"
    const val STOCK_LIST = "STOCK_LIST"
    const val PURCHASE = "PURCHASE"
    const val DELIVERY = "DELIVERY"
}

private fun now() = System.currentTimeMillis()

fun SupplierDto.toEntity() = SupplierEntity(
    id, name, contactPerson, mobile, altPhone, address, city, gstin, notes, active, deleted, version,
    SyncState.SYNCED, null, now(), createdBy, updatedBy,
)

fun SupplierEntity.toInput() = SupplierInput(name, contactPerson, mobile, altPhone, address, city, gstin, notes, active)

fun ItemDto.toEntity() = ItemEntity(
    id, name, category, defaultUnitId, latestPurchasePrice, latestSellingPrice, lastPurchaseDate,
    preferredSupplierId, notes, active, deleted, version, SyncState.SYNCED, null, now(), createdBy, updatedBy,
)

fun ItemEntity.toInput() = ItemInput(name, category, defaultUnitId, latestPurchasePrice, latestSellingPrice,
    preferredSupplierId, notes, active)

fun UnitDto.toEntity() = UnitEntity(id, code, name, deleted, version, SyncState.SYNCED, null)

fun StockListDto.toEntity() = StockListEntity(
    id, title, listDate, supplierId, notes, status, receivingStatus, totalAmount, createdBy, deleted, version,
    SyncState.SYNCED, null, now(), updatedBy,
)

fun StockListDto.lineEntities() = lines.map {
    StockListLineEntity(it.id, id, it.itemId, it.itemName, it.description, it.quantity, it.unitId, it.priceBasis,
        it.enteredPrice, it.lineTotal, it.sortOrder, it.receivedQuantity, it.unitMismatch)
}

fun StockListEntity.toInput(lines: List<StockListLineEntity>) = StockListInput(
    title, listDate, supplierId, notes, status,
    lines.sortedBy { it.sortOrder }.map {
        LineInput(it.id, it.itemId, it.itemName, it.description, it.quantity, it.unitId, it.priceBasis, it.enteredPrice)
    },
)

fun PurchaseDto.toEntity() = PurchaseEntity(
    id, supplierId, supplierName, invoiceNo, purchaseDate, receivedDate, stockListId, notes, status, overReceived,
    duplicateInvoice, itemCount, revisionNo, totalCost, totalExpectedSales, totalExpectedProfit, marginPercent,
    markupPercent, cancelReason, createdBy, deleted, version, SyncState.SYNCED, null, System.currentTimeMillis(),
    updatedBy, completedBy,
)

fun PurchaseDto.lineEntities() = lines.map {
    PurchaseLineEntity(it.id, id, it.itemId, it.itemName, it.unitId, it.unitCode, it.quantity, it.priceBasis,
        it.purchasePrice, it.purchaseUnitPrice, it.sellingPrice, it.totalCost, it.expectedSales, it.expectedProfit,
        it.marginPercent, it.markupPercent, it.stockListItemId, it.updateCatalog, it.sortOrder)
}

fun PurchaseDto.deliveryEntities() = deliveries.mapIndexed { i, d ->
    DeliveryEntity(d.id, id, d.receivedDate, d.receivedBy, d.notes, pending = false, createdAt = i.toLong())
}

fun PurchaseDto.deliveryLineEntities() = deliveries.flatMap { d ->
    d.lines.map {
        DeliveryLineEntity(it.id, d.id, id, it.purchaseLineId, it.kind, it.quantity, it.correctsId, it.reason,
            it.batchNo, it.expiryDate, pending = false)
    }
}

fun PurchaseEntity.toInput(lines: List<PurchaseLineEntity>, receive: DeliveryInput? = null) = PurchaseInput(
    supplierId, invoiceNo, purchaseDate, stockListId, notes, status != "DRAFT",
    lines.sortedBy { it.sortOrder }.map {
        PurchaseLineInput(it.id, it.itemId, it.quantity, it.unitId, it.priceBasis, it.purchasePrice, it.sellingPrice,
            it.stockListItemId, it.updateCatalog)
    },
    receive,
)

fun BatchDto.toEntity() = BatchEntity(
    id, itemId, itemName ?: "Item", purchaseId, purchaseLineId, deliveryId, supplierId, supplierName, invoiceNo,
    purchaseDate, receivedDate, receivedBy, batchNo, receivedQuantity ?: quantity, quantity, unitCode.orEmpty(),
    expiryDate, cancelled, pending = false,
)

fun PersonDto.toEntity() = PersonEntity(id, fullName, role, active)
