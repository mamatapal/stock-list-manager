package app.stocklist.app

import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.BatchDto
import app.stocklist.app.data.remote.DeliveryDto
import app.stocklist.app.data.remote.PersonDto
import app.stocklist.app.data.remote.DeliveryInput
import app.stocklist.app.data.remote.DeliveryLineDto
import app.stocklist.app.data.remote.PurchaseDto
import app.stocklist.app.data.remote.PurchaseLineDto
import app.stocklist.app.domain.catalogue.PurchaseCalculator
import app.stocklist.app.domain.catalogue.ReceivingCalculator
import app.stocklist.app.data.remote.ItemDto
import app.stocklist.app.data.remote.LineDto
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.PullResponse
import app.stocklist.app.data.remote.PushOp
import app.stocklist.app.data.remote.PushRequest
import app.stocklist.app.data.remote.PushResponse
import app.stocklist.app.data.remote.PushResult
import app.stocklist.app.data.remote.StockListDto
import app.stocklist.app.data.remote.SupplierDto
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.ListCalculator
import java.lang.reflect.Proxy
import kotlin.coroutines.intrinsics.intercepted
import java.math.BigDecimal

/**
 * A small stand-in for the real sync endpoints (versions, change sequence, permission checks, idempotent op ids)
 * so two "phones" can be tested against one server without a network. The real server rules are tested in the
 * backend's SyncIT; this checks the app's side of the protocol.
 */
class FakeServer {
    data class Rec(val type: String, var seq: Long, var supplier: SupplierDto? = null, var item: ItemDto? = null,
                           var list: StockListDto? = null, var purchase: PurchaseDto? = null)

    private val records = linkedMapOf<String, Rec>()
    private val applied = mutableMapOf<String, PushResult>()
    private var seq = 0L
    var offline = false
    /** The next N uploads are applied, but the reply is lost on the way back (the phone sees a network error). */
    var loseResponses = 0
    /** Ops for which the server answers RETRY (busy or an internal error); nothing is saved or remembered. */
    var retryWhen: ((PushOp) -> Boolean)? = null
    val received = mutableListOf<PushOp>()
    /** Names of catalogue items that exist only on a test phone (the real server knows every item). */
    val itemNames = mutableMapOf<String, String>()
    /** The business's people, sent with every pull like the real server. */
    val people = mutableListOf<PersonDto>()

    fun record(id: String) = records[id]

    /** Changes when the server is "restored from a backup" (see [restoreWithout]). */
    var dataEpoch = "epoch-1"

    /** Simulates restoring an older backup: records made after it are gone and the data epoch changes. */
    @Synchronized
    fun restoreWithout(vararg ids: String) {
        ids.forEach { records.remove(it) }
        dataEpoch = "epoch-" + java.util.UUID.randomUUID()
    }
    fun records(): List<Rec> = records.values.toList()

    fun apiFor(user: () -> MeDto): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader, arrayOf(ApiService::class.java),
    ) { _, method, args ->
        if (offline || (method.name == "syncPush" && loseResponses > 0)) {
            if (!offline) {
                loseResponses--
                push(args[0] as PushRequest, user()) // reaches the server and is applied ...
            }
            // A Proxy may not throw checked exceptions directly; fail the suspended call instead, like Retrofit does.
            @Suppress("UNCHECKED_CAST")
            val cont = (args.last() as kotlin.coroutines.Continuation<Any?>).intercepted()
            Thread { cont.resumeWith(Result.failure(java.io.IOException("offline"))) }.start() // ... but no reply arrives
            return@newProxyInstance kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
        }
        when (method.name) {
            "syncPush" -> push(args[0] as PushRequest, user())
            "syncPull" -> pull(args[0] as Long, user())
            else -> throw UnsupportedOperationException(method.name)
        }
    } as ApiService

    private fun MeDto.has(p: String) = isOwner || p in permissions

    @Synchronized
    private fun push(req: PushRequest, user: MeDto): PushResponse = PushResponse(req.ops.map { op ->
        received += op
        // Like the real ledger: every outcome is remembered, so an op id always gets the same answer.
        val retry = applied[op.opId] == null && retryWhen?.invoke(op) == true
        (if (retry) PushResult(op.opId, "RETRY", "SERVER_ERROR", "The server could not process this change")
        else applied[op.opId] ?: apply(op, user).also { applied[op.opId] = it }).let {
            // Like the real server, profit figures are removed for users who may not see them.
            if (it.purchase != null && !user.has("EXPECTED_PROFIT_VIEW")) it.copy(purchase = it.purchase.withoutProfit()) else it
        }
    })

    private fun apply(op: PushOp, user: MeDto): PushResult {
        if (op.entityType == "PURCHASE") return applyPurchase(op, user)
        if (op.entityType == "DELIVERY") return applyDelivery(op, user)
        val prefix = when (op.entityType) { "SUPPLIER" -> "SUPPLIER"; "ITEM" -> "ITEM"; else -> "LIST" }
        val existing = records[op.entityId]
        val needed = when {
            op.action == "DELETE" -> "${prefix}_DELETE"
            existing == null -> if (prefix == "LIST") "LIST_CREATE" else "${prefix}_ADD"
            else -> "${prefix}_EDIT"
        }
        if (!user.has(needed)) return PushResult(op.opId, "REJECTED", "PERMISSION_DENIED", "You do not have permission")
        if (existing != null) {
            val current = version(existing)
            if (op.baseVersion != current) return copy(op.opId, "CONFLICT", existing)
        } else if (op.baseVersion != null) {
            return PushResult(op.opId, "REJECTED", "NOT_FOUND", "Not found")
        }
        val next = existing?.let { version(it) + 1 } ?: 0L
        val rec = existing ?: Rec(op.entityType, 0).also { records[op.entityId] = it }
        rec.seq = ++seq
        val deleted = op.action == "DELETE"
        when (op.entityType) {
            "SUPPLIER" -> {
                val s = op.supplier ?: rec.supplier!!.let { SupplierInputOf(it) }
                rec.supplier = SupplierDto(op.entityId, s.name, s.contactPerson, s.mobile, s.altPhone, s.address, s.city,
                    s.gstin, s.notes, s.active, next, deleted)
            }
            "ITEM" -> {
                val i = op.item!!
                rec.item = ItemDto(op.entityId, i.name, i.category, i.defaultUnitId, i.latestPurchasePrice, i.latestSellingPrice,
                    null, i.preferredSupplierId, i.notes, i.active, next, deleted)
            }
            "STOCK_LIST" -> {
                val old = rec.list
                val l = op.stockList
                rec.list = if (l == null) old!!.copy(version = next, deleted = true) else {
                    val lines = l.lines.mapIndexed { idx, li ->
                        val qty = BigDecimal(li.quantity)
                        val price = li.enteredPrice?.let(::BigDecimal)
                        LineDto(li.id, li.itemId, li.itemName, li.description, li.quantity, li.unitId, li.priceBasis,
                            li.enteredPrice, Decimals.money(ListCalculator.lineTotal(li.priceBasis, qty, price)),
                            Decimals.format(ListCalculator.effectiveUnitPrice(li.priceBasis, qty, price)), idx)
                    }
                    StockListDto(op.entityId, l.title, l.listDate, l.supplierId, l.notes, l.status, "PENDING",
                        Decimals.money(ListCalculator.listTotal(lines.map { it.lineTotal?.let(::BigDecimal) })), lines, next,
                        deleted, old?.createdBy ?: user.userId)
                }
            }
        }
        return copy(op.opId, "APPLIED", rec)
    }

    private fun SupplierInputOf(d: SupplierDto) =
        app.stocklist.app.data.remote.SupplierInput(d.name, d.contactPerson, d.mobile, d.altPhone, d.address, d.city, d.gstin, d.notes, d.active)

    private fun version(r: Rec) = r.supplier?.version ?: r.item?.version ?: r.list?.version ?: r.purchase!!.version

    // ---------------------------------------------------------------------------- purchases (simplified server rules)

    private fun applyPurchase(op: PushOp, user: MeDto): PushResult {
        val existing = records[op.entityId]
        val current = existing?.purchase
        val needed = when {
            op.action == "DELETE" -> "PURCHASE_DELETE"
            current == null -> "PURCHASE_RECORD"
            current.status == "DRAFT" -> "PURCHASE_RECORD"
            else -> "PURCHASE_EDIT"
        }
        if (!user.has(needed)) return PushResult(op.opId, "REJECTED", "PERMISSION_DENIED", "You do not have permission")
        if (existing != null && op.baseVersion != version(existing)) return copy(op.opId, "CONFLICT", existing)
        if (existing == null && op.baseVersion != null) return PushResult(op.opId, "REJECTED", "NOT_FOUND", "Not found")
        val rec = existing ?: Rec("PURCHASE", 0).also { records[op.entityId] = it }
        val next = current?.let { it.version + 1 } ?: 0L
        if (op.action == "DELETE") {
            val c = current!!
            if (c.status != "DRAFT" && op.reason.isNullOrBlank()) {
                return PushResult(op.opId, "REJECTED", "REASON_REQUIRED", "Give a reason")
            }
            rec.purchase = if (c.status == "DRAFT") c.copy(deleted = true, version = next)
            else c.copy(status = "CANCELLED", cancelReason = op.reason, version = next)
            rec.seq = ++seq
            return copy(op.opId, "APPLIED", rec)
        }
        val input = op.purchase!!
        val lines = input.lines.mapIndexed { i, l ->
            val c = PurchaseCalculator.line(l.priceBasis, BigDecimal(l.quantity), l.purchasePrice?.let(::BigDecimal),
                l.sellingPrice?.let(::BigDecimal))
            PurchaseLineDto(l.id, l.itemId, current?.lines?.firstOrNull { it.id == l.id && it.itemId == l.itemId }?.itemName
                    ?: records[l.itemId]?.item?.name ?: itemNames[l.itemId] ?: "Item",
                l.unitId, "PCS", l.quantity, l.priceBasis, l.purchasePrice, Decimals.format(c.unitPrice), l.sellingPrice,
                Decimals.money(c.cost), Decimals.money(c.sales), Decimals.money(c.profit), c.margin?.toPlainString(),
                c.markup?.toPlainString(), l.stockListItemId, l.updateCatalog, i)
        }
        val totals = PurchaseCalculator.totals(lines.map {
            PurchaseCalculator.line(it.priceBasis, BigDecimal(it.quantity), it.purchasePrice?.let(::BigDecimal), it.sellingPrice?.let(::BigDecimal))
        })
        var deliveries = current?.deliveries.orEmpty()
        input.receive?.let { r -> if (deliveries.none { it.id == r.id }) deliveries = deliveries + r.toDto(user) }
        val base = PurchaseDto(op.entityId, input.supplierId, "Supplier", input.invoiceNo, input.purchaseDate, null,
            input.stockListId, input.notes, if (input.complete) "PENDING" else "DRAFT", false, false, lines.size,
            (current?.revisionNo ?: 0) + if (current != null && current.status != "DRAFT") 1 else 0,
            Decimals.money(totals.cost), Decimals.money(totals.sales), Decimals.money(totals.profit),
            totals.margin?.toPlainString(), totals.markup?.toPlainString(), null, lines, deliveries, next, false,
            current?.createdBy ?: user.userId)
        rec.purchase = withStatus(base)
        rec.seq = ++seq
        return copy(op.opId, "APPLIED", rec)
    }

    private fun applyDelivery(op: PushOp, user: MeDto): PushResult {
        if (!user.has("PURCHASE_RECORD")) return PushResult(op.opId, "REJECTED", "PERMISSION_DENIED", "You do not have permission")
        val d = op.delivery!!
        val rec = records[d.purchaseId] ?: return PushResult(op.opId, "REJECTED", "NOT_FOUND", "Not found")
        val p = rec.purchase!!
        if (p.deliveries.none { it.id == d.id }) {   // the same delivery sent again is recorded once
            rec.purchase = withStatus(p.copy(deliveries = p.deliveries + d.toDto(user)))   // no new version
            rec.seq = ++seq
        }
        return PushResult(op.opId, "APPLIED", null, null, rec.purchase!!.version, purchase = rec.purchase)
    }

    /** Like the server: one batch per receipt (same id), quantity = receipt plus its corrections. */
    private fun batchesOf(p: PurchaseDto): List<BatchDto> {
        val all = p.deliveries.flatMap { d -> d.lines.map { d to it } }
        return all.filter { it.second.kind == "RECEIPT" }.map { (d, r) ->
            val l = p.lines.first { it.id == r.purchaseLineId }
            val qty = all.filter { it.second.id == r.id || it.second.correctsId == r.id }
                .fold(BigDecimal.ZERO) { a, x -> a.add(BigDecimal(x.second.quantity)) }
            BatchDto(r.id, l.itemId, l.itemName, p.id, l.id, d.id, p.supplierId, p.supplierName, p.invoiceNo, p.purchaseDate,
                p.status, d.receivedDate, d.receivedBy, r.batchNo, r.quantity, Decimals.format(qty)!!, l.unitCode, r.expiryDate,
                p.status == "CANCELLED")
        }
    }

    private fun DeliveryInput.toDto(user: MeDto) = DeliveryDto(id, receivedDate, user.userId, notes,
        lines.map { DeliveryLineDto(it.id, it.purchaseLineId, it.kind, it.quantity, it.correctsId, it.reason, it.batchNo, it.expiryDate) })

    private fun withStatus(p: PurchaseDto): PurchaseDto {
        if (p.status == "DRAFT" || p.status == "CANCELLED") return p
        val got = p.deliveries.flatMap { it.lines }.groupBy { it.purchaseLineId }
            .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, x -> a.add(BigDecimal(x.quantity)) } }
        val statuses = p.lines.map { ReceivingCalculator.progress(BigDecimal(it.quantity), got[it.id]).status }
        val status = when (ReceivingCalculator.overall(statuses)) { "FULL" -> "RECEIVED"; "PARTIAL" -> "PARTIAL"; else -> "PENDING" }
        return p.copy(status = status, receivedDate = p.deliveries.maxOfOrNull { it.receivedDate })
    }

    private fun copy(opId: String, status: String, r: Rec) =
        PushResult(opId, status, if (status == "CONFLICT") "VERSION_CONFLICT" else null,
            if (status == "CONFLICT") "Changed by someone else" else null, version(r), r.supplier, r.item, null, r.list, r.purchase)

    @Synchronized
    private fun pull(since: Long, user: MeDto): PullResponse {
        val newer = records.values.filter { it.seq > since }
        val lists = user.has("LIST_CREATE") || user.has("LIST_EDIT") || user.has("LIST_DELETE") || user.has("PURCHASE_RECORD")
        val purchases = user.has("PURCHASE_HISTORY_VIEW") || user.has("PURCHASE_RECORD") || user.has("PURCHASE_EDIT") || user.has("PURCHASE_DELETE")
        val profit = user.has("EXPECTED_PROFIT_VIEW")
        return PullResponse(
            suppliers = if (user.has("SUPPLIER_VIEW")) newer.mapNotNull { it.supplier } else emptyList(),
            items = if (user.has("ITEM_VIEW")) newer.mapNotNull { it.item } else emptyList(),
            units = emptyList(),
            stockLists = if (lists) newer.mapNotNull { it.list } else emptyList(),
            purchases = if (purchases) newer.mapNotNull { it.purchase }.map { if (profit) it else it.withoutProfit() } else emptyList(),
            batches = if (user.has("EXPIRY_VIEW")) newer.mapNotNull { it.purchase }.flatMap { batchesOf(it) } else emptyList(),
            people = people.toList(),
            nextSince = seq,
            hasMore = false,
            dataEpoch = dataEpoch,
        )
    }
}

/** What the real server sends to users without EXPECTED_PROFIT_VIEW. */
fun PurchaseDto.withoutProfit() = copy(totalExpectedSales = null, totalExpectedProfit = null, marginPercent = null,
    markupPercent = null, lines = lines.map { it.copy(expectedSales = null, expectedProfit = null, marginPercent = null, markupPercent = null) })
