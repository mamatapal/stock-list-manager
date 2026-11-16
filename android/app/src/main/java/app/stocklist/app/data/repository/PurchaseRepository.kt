package app.stocklist.app.data.repository

import app.stocklist.app.core.i18n.tr

import androidx.room.withTransaction
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.BatchEntity
import app.stocklist.app.data.local.DeliveryEntity
import app.stocklist.app.data.local.DeliveryLineEntity
import app.stocklist.app.data.local.PurchaseEntity
import app.stocklist.app.data.local.PurchaseLineEntity
import app.stocklist.app.data.local.StockListLineEntity
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.remote.DeliveryInput
import app.stocklist.app.data.remote.DeliveryLineInput
import app.stocklist.app.data.remote.PurchaseInput
import app.stocklist.app.data.sync.EntityTypes
import app.stocklist.app.data.sync.Outbox
import app.stocklist.app.data.sync.SyncScheduler
import app.stocklist.app.data.sync.toInput
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.catalogue.ReceivingCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One line as the purchase editor holds it. Numbers are the text the user typed. */
data class PurchaseLineDraft(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val unitId: String,
    val quantity: String = "",
    val priceBasis: String = PurchaseCalculator.PER_UNIT,
    val purchasePrice: String = "",
    val sellingPrice: String = "",
    /** Received with the bill (only used when completing with "goods received now"). */
    val receivedNow: String = "",
    val batchNo: String = "",
    val expiryDate: String = "",
    val stockListItemId: String? = null,
    val updateCatalog: Boolean = true,
)

data class PurchaseHeaderDraft(
    val supplierId: String?,
    val invoiceNo: String,
    val purchaseDate: LocalDate,
    val receivedDate: LocalDate,
    val stockListId: String?,
    val notes: String,
)

data class ReceiveDraft(val purchaseLineId: String, val quantity: String, val batchNo: String, val expiryDate: String)

/** A purchase with everything the detail screen needs; received quantities include unsent deliveries. */
data class PurchaseView(
    val purchase: PurchaseEntity,
    val lines: List<PurchaseLineEntity>,
    val deliveries: List<DeliveryEntity>,
    val deliveryLines: List<DeliveryLineEntity>,
) {
    val received: Map<String, BigDecimal> = deliveryLines.groupBy { it.purchaseLineId }
        .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, d -> a.add(BigDecimal(d.quantity)) } }

    fun progress(line: PurchaseLineEntity) = ReceivingCalculator.progress(BigDecimal(line.quantity), received[line.id])

    val hasUnsent: Boolean get() = deliveries.any { it.pending } || purchase.syncState != SyncState.SYNCED
}

/** Receiving progress of one out-of-stock list line. */
data class ListLineProgress(val progress: ReceivingCalculator.Progress, val unitMismatch: Boolean)

/**
 * Purchases and deliveries. Like the other repositories, every change is written to Room together with its outbox
 * entry in one transaction; the server re-checks permissions, recalculates every number and keeps price snapshots.
 * A purchase completed with goods received now is ONE server operation (purchase + delivery saved together).
 */
@Singleton
class PurchaseRepository @Inject constructor(
    private val db: AppDatabase,
    private val outbox: Outbox,
    private val json: Json,
    private val scheduler: SyncScheduler,
) {
    fun observe(q: String, status: String, supplierId: String, from: String, to: String): Flow<List<PurchaseEntity>> =
        db.purchases().observe(q.trim(), status, supplierId, from, to)

    fun observeOne(id: String): Flow<PurchaseView?> = combine(
        db.purchases().observeOne(id), db.purchases().observeLines(id), db.purchases().observeDeliveries(id),
        db.purchases().observeDeliveryLines(id),
    ) { p, l, d, dl -> p?.let { PurchaseView(it, l, d, dl) } }

    suspend fun get(id: String) = db.purchases().get(id)
    suspend fun lines(id: String) = db.purchases().lines(id)
    suspend fun received(id: String): Map<String, BigDecimal> {
        val p = db.purchases().get(id) ?: return emptyMap()
        return PurchaseView(p, emptyList(), emptyList(), db.purchases().deliveryLines(id)).received
    }

    /**
     * Requested / received / remaining per list line: the server's confirmed figure plus deliveries recorded on
     * this phone that the server has not confirmed yet (same unit only).
     */
    fun observeListProgress(lines: Flow<List<StockListLineEntity>>): Flow<Map<String, ListLineProgress>> =
        combine(lines, db.purchases().observeAllLines(), db.purchases().observePendingDeliveryLines()) { ls, pls, pending ->
            val byPurchaseLine = pending.groupBy { it.purchaseLineId }
            ls.associate { l ->
                var received = BigDecimal(l.receivedQuantity)
                var mismatch = l.unitMismatch
                pls.filter { it.stockListItemId == l.id }.forEach { pl ->
                    val extra = byPurchaseLine[pl.id].orEmpty().fold(BigDecimal.ZERO) { a, d -> a.add(BigDecimal(d.quantity)) }
                    if (pl.unitId == l.unitId) received = received.add(extra) else if (extra.signum() != 0) mismatch = true
                }
                l.id to ListLineProgress(ReceivingCalculator.progress(BigDecimal(l.quantity), received), mismatch)
            }
        }

    /**
     * Saves a draft (complete = false) or a completed purchase. With [receiveNow] the "received now" quantities,
     * batch numbers and expiry dates are recorded as a delivery in the same operation.
     * Throws IllegalArgumentException with a readable message when something is invalid.
     */
    suspend fun save(
        id: String?, header: PurchaseHeaderDraft, lines: List<PurchaseLineDraft>, complete: Boolean,
        receiveNow: Boolean, createdBy: String?, showProfit: Boolean,
    ): String {
        val supplierId = header.supplierId ?: throw IllegalArgumentException(tr("Choose a supplier"))
        require(!complete || lines.isNotEmpty()) { tr("Add at least one item before completing the purchase") }
        require(header.invoiceNo.trim().length <= 60) { tr("Invoice number: at most 60 characters") }
        val newId = id ?: UUID.randomUUID().toString()
        val existing = db.purchases().get(newId)
        require(existing?.status != "CANCELLED") { tr("A cancelled purchase cannot be changed") }
        val wasCompleted = existing != null && existing.status != "DRAFT"
        require(!wasCompleted || complete) { tr("A completed purchase cannot go back to draft") }
        val alreadyReceived = if (existing != null) received(newId) else emptyMap()
        val oldLines = if (existing != null) db.purchases().lines(newId).associateBy { it.id } else emptyMap()
        val customUnits = mutableMapOf<String, String>()

        val calcs = mutableListOf<PurchaseCalculator.Line>()
        val entities = lines.mapIndexed { i, l ->
            val name = l.itemName
            val qty = Decimals.parse(l.quantity, "$name quantity", 3, false)
                ?: throw IllegalArgumentException(tr("%1\$s: enter a quantity", name))
            val buy = Decimals.parse(l.purchasePrice, tr("%1\$s purchase price", name), PurchaseCalculator.priceScale(l.priceBasis), true)
            val sell = Decimals.parse(l.sellingPrice, tr("%1\$s selling price", name), 4, true)
            val got = alreadyReceived[l.id] ?: BigDecimal.ZERO
            if (got.signum() != 0) {
                require(qty >= got) { tr("%1\$s: the billed quantity cannot be less than the %2\$s already received", name, Decimals.format(got)) }
                val old = oldLines[l.id]
                require(old == null || (old.unitId == l.unitId && old.itemId == l.itemId)) {
                    tr("%1\$s: goods were already received, so the item and unit cannot change", name)
                }
            }
            val c = PurchaseCalculator.line(l.priceBasis, qty, buy, sell)
            calcs += c
            val old = oldLines[l.id]
            val unitCode = BuiltInUnits.all.firstOrNull { it.id == l.unitId }?.code
                ?: customUnits.getOrPut(l.unitId) { db.units().get(l.unitId)?.code ?: "?" }
            PurchaseLineEntity(
                l.id, newId, l.itemId,
                // keep the name a line was first saved with (the server does the same)
                if (old != null && old.itemId == l.itemId) old.itemName else name,
                l.unitId, if (old != null && old.unitId == l.unitId) old.unitCode else unitCode,
                Decimals.format(qty)!!, l.priceBasis, Decimals.format(buy), Decimals.format(c.unitPrice), Decimals.format(sell),
                Decimals.money(c.cost), Decimals.money(c.sales).takeIf { showProfit },
                Decimals.money(c.profit).takeIf { showProfit }, c.margin?.toPlainString()?.takeIf { showProfit },
                c.markup?.toPlainString()?.takeIf { showProfit }, l.stockListItemId, l.updateCatalog, i,
            )
        }
        oldLines.values.filter { o -> lines.none { it.id == o.id } }.forEach { removed ->
            require((alreadyReceived[removed.id] ?: BigDecimal.ZERO).signum() == 0) {
                tr("%1\$s: goods were already received for this line, so it cannot be removed", removed.itemName)
            }
        }

        // Goods that came with the bill.
        var receive: DeliveryInput? = null
        var delivery: DeliveryEntity? = null
        var deliveryLines: List<DeliveryLineEntity> = emptyList()
        if (complete && receiveNow) {
            val deliveryId = UUID.randomUUID().toString()
            val dl = lines.mapNotNull { l ->
                val q = Decimals.parse(l.receivedNow, tr("%1\$s received quantity", l.itemName), 3, true)
                if (q == null || q.signum() == 0) return@mapNotNull null
                val expiry = l.expiryDate.trim().ifEmpty { null }
                expiry?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException(tr("%1\$s: invalid expiry date", l.itemName)) } }
                DeliveryLineEntity(UUID.randomUUID().toString(), deliveryId, newId, l.id, "RECEIPT", Decimals.format(q)!!,
                    null, null, l.batchNo.trim().ifEmpty { null }, expiry, pending = true)
            }
            if (dl.isNotEmpty()) {
                delivery = DeliveryEntity(deliveryId, newId, header.receivedDate.toString(), createdBy, null, true,
                    System.currentTimeMillis())
                deliveryLines = dl
                receive = DeliveryInput(deliveryId, null, header.receivedDate.toString(), null, dl.map {
                    DeliveryLineInput(it.id, it.purchaseLineId, "RECEIPT", it.quantity, null, null, it.batchNo, it.expiryDate)
                })
            }
        }

        val totals = PurchaseCalculator.totals(calcs)
        db.withTransaction {
            val entity = PurchaseEntity(
                newId, supplierId, db.suppliers().get(supplierId)?.name ?: existing?.supplierName,
                header.invoiceNo.trim().ifEmpty { null }, header.purchaseDate.toString(), existing?.receivedDate,
                header.stockListId, header.notes.trim().ifEmpty { null }, if (complete) "PENDING" else "DRAFT",
                existing?.overReceived ?: false, existing?.duplicateInvoice ?: false, entities.size,
                existing?.revisionNo ?: 0, Decimals.money(totals.cost),
                Decimals.money(totals.sales).takeIf { showProfit }, Decimals.money(totals.profit).takeIf { showProfit },
                totals.margin?.toPlainString()?.takeIf { showProfit }, totals.markup?.toPlainString()?.takeIf { showProfit },
                null, existing?.createdBy ?: createdBy, false, existing?.serverVersion, SyncState.PENDING, null,
                System.currentTimeMillis(), updatedBy = createdBy,
                completedBy = existing?.completedBy ?: if (complete) createdBy else null,
            )
            db.purchases().upsert(entity)
            db.purchases().deleteLines(newId)
            db.purchases().insertLines(entities)
            if (delivery != null) {
                db.purchases().insertDelivery(delivery)
                db.purchases().insertDeliveryLines(deliveryLines)
                writeBatches(entity, entities, delivery, deliveryLines)
            }
            refreshLocalStatus(newId)
            val payload: PurchaseInput = entity.toInput(entities, receive)
            outbox.enqueue(EntityTypes.PURCHASE, newId, "UPSERT", existing?.serverVersion == null,
                json.encodeToString(PurchaseInput.serializer(), payload))
        }
        scheduler.requestNow()
        return newId
    }

    /** Records goods that arrived later (a partial or final delivery). */
    suspend fun receive(purchaseId: String, receivedDate: LocalDate, notes: String, lines: List<ReceiveDraft>, userId: String?) {
        val purchase = db.purchases().get(purchaseId) ?: throw IllegalArgumentException(tr("Purchase not found"))
        require(purchase.status != "DRAFT") { tr("Complete the purchase before receiving goods") }
        require(purchase.status != "CANCELLED") { tr("A cancelled purchase cannot receive goods") }
        val names = db.purchases().lines(purchaseId).associate { it.id to it.itemName }
        val deliveryId = UUID.randomUUID().toString()
        val dl = lines.mapNotNull { l ->
            val name = names[l.purchaseLineId] ?: return@mapNotNull null
            val q = Decimals.parse(l.quantity, tr("%1\$s received quantity", name), 3, true)
            if (q == null || q.signum() == 0) return@mapNotNull null
            val expiry = l.expiryDate.trim().ifEmpty { null }
            expiry?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException(tr("%1\$s: invalid expiry date", name)) } }
            DeliveryLineEntity(UUID.randomUUID().toString(), deliveryId, purchaseId, l.purchaseLineId, "RECEIPT",
                Decimals.format(q)!!, null, null, l.batchNo.trim().ifEmpty { null }, expiry, pending = true)
        }
        require(dl.isNotEmpty()) { tr("Enter at least one received quantity") }
        writeDelivery(purchaseId, deliveryId, receivedDate, notes, dl, userId)
    }

    /**
     * Fixes an earlier receipt without changing it: a signed correction (e.g. -10) with a reason. The received
     * quantity of the line can never go below zero.
     */
    suspend fun correct(purchaseId: String, receiptLineId: String, quantity: String, reason: String, userId: String?) {
        require(reason.isNotBlank()) { tr("Give a reason for the correction") }
        val all = db.purchases().deliveryLines(purchaseId)
        val receipt = all.firstOrNull { it.id == receiptLineId && it.kind == "RECEIPT" }
            ?: throw IllegalArgumentException(tr("Choose the receipt to correct"))
        val v = quantity.trim()
        val negative = v.startsWith("-")
        val abs = Decimals.parse(if (negative) v.substring(1) else v, tr("Correction"), 3, false)
            ?: throw IllegalArgumentException(tr("Enter the correction, for example -10"))
        val q = if (negative) abs.negate() else abs
        val receiptTotal = all.filter { it.id == receipt.id || it.correctsId == receipt.id }
            .fold(BigDecimal.ZERO) { a, d -> a.add(BigDecimal(d.quantity)) }
        require(receiptTotal.add(q).signum() >= 0) { tr("The correction would make the received quantity negative") }
        val deliveryId = UUID.randomUUID().toString()
        writeDelivery(purchaseId, deliveryId, LocalDate.now(), "", listOf(
            DeliveryLineEntity(UUID.randomUUID().toString(), deliveryId, purchaseId, receipt.purchaseLineId, "CORRECTION",
                Decimals.format(q)!!, receipt.id, reason.trim(), null, null, pending = true),
        ), userId)
    }

    private suspend fun writeDelivery(
        purchaseId: String, deliveryId: String, receivedDate: LocalDate, notes: String, lines: List<DeliveryLineEntity>,
        userId: String?,
    ) {
        db.withTransaction {
            db.purchases().insertDelivery(DeliveryEntity(deliveryId, purchaseId, receivedDate.toString(), userId,
                notes.trim().ifEmpty { null }, true, System.currentTimeMillis()))
            db.purchases().insertDeliveryLines(lines)
            db.purchases().get(purchaseId)?.let { p ->
                writeBatches(p, db.purchases().lines(purchaseId), db.purchases().getDelivery(deliveryId)!!, lines)
            }
            refreshLocalStatus(purchaseId)
            val input = DeliveryInput(deliveryId, purchaseId, receivedDate.toString(), notes.trim().ifEmpty { null }, lines.map {
                DeliveryLineInput(it.id, it.purchaseLineId, it.kind, it.quantity, it.correctsId, it.reason, it.batchNo, it.expiryDate)
            })
            outbox.enqueue(EntityTypes.DELIVERY, deliveryId, "UPSERT", true, json.encodeToString(DeliveryInput.serializer(), input))
        }
        scheduler.requestNow()
    }

    /** Deletes a draft, or cancels a completed purchase with a reason (it is never deleted). */
    suspend fun deleteOrCancel(id: String, reason: String?) {
        val p = db.purchases().get(id) ?: return
        if (p.status != "DRAFT") require(!reason.isNullOrBlank()) { tr("Give a reason for cancelling this purchase") }
        db.withTransaction {
            if (p.serverVersion == null) {
                // never reached the server: just forget it, including goods recorded against it
                db.purchases().deliveryLines(id).map { it.deliveryId }.toSet().forEach { db.outbox().deleteForEntity(it) }
                db.outbox().deleteForEntity(id)
                db.batches().deleteForPurchase(id)
                db.purchases().deleteAllDeliveryLines(id)
                db.purchases().deleteAllDeliveries(id)
                db.purchases().deleteLines(id)
                db.purchases().delete(id)
            } else if (p.status == "DRAFT") {
                db.purchases().upsert(p.copy(deleted = true, syncState = SyncState.PENDING))
                outbox.enqueue(EntityTypes.PURCHASE, id, "DELETE", false, null)
            } else {
                db.purchases().upsert(p.copy(status = "CANCELLED", cancelReason = reason!!.trim(), syncState = SyncState.PENDING))
                db.batches().cancelForPurchase(id)
                outbox.enqueue(EntityTypes.PURCHASE, id, "DELETE", false, reason.trim())
            }
        }
        scheduler.requestNow()
    }

    /**
     * Batches for goods recorded on this phone, shown before the server confirms them. A receipt creates a batch
     * with the receipt line's id (the server uses the same id); a correction changes the corrected batch.
     */
    private suspend fun writeBatches(
        p: PurchaseEntity, lines: List<PurchaseLineEntity>, d: DeliveryEntity, dls: List<DeliveryLineEntity>,
    ) {
        val byId = lines.associateBy { it.id }
        dls.forEach { dl ->
            if (dl.kind == "RECEIPT") {
                val l = byId[dl.purchaseLineId] ?: return@forEach
                db.batches().upsert(BatchEntity(dl.id, l.itemId, l.itemName, p.id, l.id, d.id, p.supplierId, p.supplierName,
                    p.invoiceNo, p.purchaseDate, d.receivedDate, d.receivedBy, dl.batchNo, dl.quantity, dl.quantity,
                    l.unitCode, dl.expiryDate, cancelled = false, pending = true))
            } else {
                val b = dl.correctsId?.let { db.batches().get(it) } ?: return@forEach
                db.batches().upsert(b.copy(quantity = Decimals.format(BigDecimal(b.quantity).add(BigDecimal(dl.quantity)))!!))
            }
        }
    }

    /** Status shown before the server confirms: derived from local deliveries the same way the server does. */
    private suspend fun refreshLocalStatus(purchaseId: String) {
        val p = db.purchases().get(purchaseId) ?: return
        if (p.status == "DRAFT" || p.status == "CANCELLED") return
        val lines = db.purchases().lines(purchaseId)
        val dls = db.purchases().deliveryLines(purchaseId)
        val view = PurchaseView(p, lines, emptyList(), dls)
        val statuses = lines.map { view.progress(it).status }
        val status = when (ReceivingCalculator.overall(statuses)) {
            ReceivingCalculator.FULL -> "RECEIVED"
            ReceivingCalculator.PARTIAL -> "PARTIAL"
            else -> "PENDING"
        }
        val over = lines.any { view.progress(it).overBy.signum() > 0 }
        val dates = db.purchases().deliveryLines(purchaseId).map { it.deliveryId }.toSet()
            .mapNotNull { db.purchases().getDelivery(it)?.receivedDate }
        db.purchases().upsert(p.copy(status = status, overReceived = over, receivedDate = dates.maxOrNull() ?: p.receivedDate))
    }
}
