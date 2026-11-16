package app.stocklist.app.data.sync

import app.stocklist.app.core.i18n.trKey
import androidx.room.withTransaction
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.LocalMeta
import app.stocklist.app.data.local.OutboxEntity
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.ItemDto
import app.stocklist.app.data.remote.MeDto
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.PullResponse
import app.stocklist.app.data.remote.DeliveryInput
import app.stocklist.app.data.remote.PurchaseDto
import app.stocklist.app.data.remote.PurchaseInput
import app.stocklist.app.data.remote.PushOp
import app.stocklist.app.data.remote.PushRequest
import app.stocklist.app.data.remote.PushResult
import app.stocklist.app.data.remote.StockListDto
import app.stocklist.app.data.remote.StockListInput
import app.stocklist.app.data.remote.SupplierDto
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.remote.UnitDto
import app.stocklist.app.data.remote.UnitInput
import app.stocklist.app.data.remote.apiCall
import app.stocklist.app.domain.permission.Permission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(val running: Boolean = false, val lastError: String? = null, val offline: Boolean = false)

/**
 * Push: sends queued changes in order, at most one per record per round (so each uses the version the
 * previous one produced). A record shows "synced" only after the server answers APPLIED.
 * Pull: fetches everyone else's changes after the saved cursor. A record with unsent local changes is never
 * overwritten by a pull; its own push decides.
 */
@Singleton
class SyncRepository(
    private val db: AppDatabase,
    private val api: ApiService,
    private val json: Json,
    private val currentUser: () -> MeDto?,
) {
    @Inject
    constructor(db: AppDatabase, api: ApiService, session: SessionStore, json: Json) :
        this(db, api, json, { (session.state.value as? SessionState.SignedIn)?.user })

    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val pendingCount = db.outbox().observePendingCount()
    val unsentCount = db.outbox().observeCount()
    val unsentChanges = db.outbox().observeAll().map { UnsentChanges.of(it) }
    val outboxItems = db.outbox().observeAll()
    val problems = db.outbox().observeProblems()
    val lastSync = db.metaFlow().observe(AppDatabase.META_LAST_SYNC)

    /** Returns false when the server could not be reached (the worker will retry later). */
    suspend fun syncNow(): Boolean = mutex.withLock {
        val user = currentUser() ?: return true
        _status.value = _status.value.copy(running = true)
        val perms = if (user.isOwner) Permission.entries.toSet() else Permission.fromCodes(user.permissions)
        val pushError = push()
        // Still fetch other people's changes after a refused push, but not when the server is unreachable.
        val error = if (pushError?.first == true) pushError else pull(perms) ?: pushError
        _status.value = SyncStatus(running = false, lastError = error?.second, offline = error?.first == true)
        if (error?.first != true) {
            db.meta().put(LocalMeta(AppDatabase.META_LAST_SYNC, System.currentTimeMillis().toString()))
        }
        error?.first != true
    }

    // ------------------------------------------------------------------------------------- push

    /** Returns (isNetworkError, message) on failure, null when everything that could be sent was sent. */
    private suspend fun push(): Pair<Boolean, String>? {
        // Changes the server asked to retry are not sent again in this run (they wait for the next one).
        val later = mutableSetOf<String>()
        repeat(MAX_ROUNDS) {
            val all = db.outbox().all()
            val blocked = all.filter { it.state != "PENDING" || it.opId in later }.map { it.entityId }.toSet()
            val batch = mutableListOf<Pair<OutboxEntity, PushOp>>()
            val taken = mutableSetOf<String>()
            for (op in all) {
                if (op.state != "PENDING" || op.entityId in blocked || !taken.add(op.entityId)) continue
                val base = if (op.isCreate) null else serverVersion(op.entityType, op.entityId)
                if (!op.isCreate && base == null) continue // its create has not been accepted yet
                batch += op to toPushOp(op, base)
                if (batch.size == 50) break
            }
            if (batch.isEmpty()) return null
            when (val r = apiCall { api.syncPush(PushRequest(batch.map { it.second })) }) {
                is ApiResult.Failure -> {
                    batch.forEach { db.outbox().noteAttempt(it.first.opId, r.error.message) }
                    return r.error.isNetwork to r.error.message
                }
                is ApiResult.Ok -> {
                    val byOp = r.value.results.associateBy { it.opId }
                    for ((op, _) in batch) byOp[op.opId]?.let { if (it.status == "RETRY") later += op.opId; handleResult(op, it) }
                }
            }
        }
        return null
    }

    private fun toPushOp(op: OutboxEntity, base: Long?): PushOp {
        val p = op.payloadJson
        return PushOp(
            opId = op.opId, entityType = op.entityType, entityId = op.entityId, action = op.action, baseVersion = base,
            supplier = if (op.entityType == EntityTypes.SUPPLIER && p != null) json.decodeFromString(SupplierInput.serializer(), p) else null,
            item = if (op.entityType == EntityTypes.ITEM && p != null) json.decodeFromString(ItemInput.serializer(), p) else null,
            unit = if (op.entityType == EntityTypes.UNIT && p != null) json.decodeFromString(UnitInput.serializer(), p) else null,
            stockList = if (op.entityType == EntityTypes.STOCK_LIST && p != null) json.decodeFromString(StockListInput.serializer(), p) else null,
            purchase = if (op.entityType == EntityTypes.PURCHASE && op.action == "UPSERT" && p != null) json.decodeFromString(PurchaseInput.serializer(), p) else null,
            delivery = if (op.entityType == EntityTypes.DELIVERY && p != null) json.decodeFromString(DeliveryInput.serializer(), p) else null,
            // A cancellation carries its reason as the payload.
            reason = if (op.entityType == EntityTypes.PURCHASE && op.action == "DELETE") p else null,
        )
    }

    private suspend fun handleResult(op: OutboxEntity, r: PushResult) = db.withTransaction {
        when (r.status) {
            "APPLIED" -> {
                db.outbox().delete(op.opId)
                val more = db.outbox().countForEntity(op.entityId) > 0
                if (more) setServerVersion(op.entityType, op.entityId, r.version)
                else applyServerCopy(op.entityType, op.entityId, r)
            }
            "CONFLICT" -> {
                db.outbox().mark(op.opId, "CONFLICT", r.message, copyJson(r), r.version)
                setState(op.entityType, op.entityId, SyncState.CONFLICT, r.message)
            }
            "RETRY" -> {
                // Nothing was saved on the server. Kept as pending and sent again later; after repeated server
                // errors it waits for the user (shown under "needs attention" with "Try again"), never dropped.
                db.outbox().noteServerRetry(op.opId, r.message ?: trKey("Server busy"))
                if ((db.outbox().get(op.opId)?.serverRetries ?: 0) >= MAX_SERVER_RETRIES) {
                    db.outbox().mark(op.opId, "FAILED", trKey("The server could not process this change after several tries. It is kept on this phone; tap Try again later."), null, null)
                    setState(op.entityType, op.entityId, SyncState.FAILED, trKey("Server error; kept on this phone"))
                }
            }
            else -> {
                db.outbox().mark(op.opId, "FAILED", r.message ?: r.code, null, null)
                setState(op.entityType, op.entityId, SyncState.FAILED, r.message ?: r.code)
            }
        }
    }

    // ------------------------------------------------------------------------------------- pull

    private suspend fun pull(perms: Set<Permission>): Pair<Boolean, String>? {
        // If the owner changed this user's permissions, records that were hidden before may now be visible:
        // start again from the beginning so they are fetched.
        val permKey = perms.map { it.name }.sorted().joinToString(",")
        if (db.meta().get(META_PERMS) != permKey) {
            db.meta().put(LocalMeta(AppDatabase.META_SYNC_CURSOR, "0"))
            db.meta().put(LocalMeta(META_PERMS, permKey))
        }
        var since = db.meta().get(AppDatabase.META_SYNC_CURSOR)?.toLongOrNull() ?: 0L
        repeat(MAX_PAGES) {
            val page = when (val r = apiCall { api.syncPull(since, 500) }) {
                is ApiResult.Failure -> return r.error.isNetwork to r.error.message
                is ApiResult.Ok -> r.value
            }
            val known = db.meta().get(META_EPOCH)
            if (page.dataEpoch != null && known != page.dataEpoch) {
                db.withTransaction {
                    // The server was restored from a backup: its records and change counter went back in time.
                    // Drop what was downloaded before (unsent changes stay) and fetch everything again.
                    if (known != null) dropDownloadedCopies()
                    db.meta().put(LocalMeta(META_EPOCH, page.dataEpoch))
                    db.meta().put(LocalMeta(AppDatabase.META_SYNC_CURSOR, "0"))
                }
                if (known != null && since != 0L) {
                    since = 0L
                    return@repeat
                }
            }
            db.withTransaction {
                applyPage(page)
                db.meta().put(LocalMeta(AppDatabase.META_SYNC_CURSOR, page.nextSince.toString()))
            }
            since = page.nextSince
            if (!page.hasMore) {
                purgeHidden(perms)
                return null
            }
        }
        return null
    }

    private suspend fun dropDownloadedCopies() {
        val r = db.resync()
        r.batches(); r.deliveryLines(); r.deliveries(); r.purchaseLines(); r.purchases()
        r.listLines(); r.lists(); r.items(); r.suppliers(); r.units()
    }

    private suspend fun applyPage(page: PullResponse) {
        page.suppliers.forEach { if (!hasLocalChanges(it.id)) applySupplier(it) }
        page.items.forEach { if (!hasLocalChanges(it.id)) applyItem(it) }
        page.units.forEach { if (!hasLocalChanges(it.id)) applyUnit(it) }
        page.stockLists.forEach { if (!hasLocalChanges(it.id)) applyList(it) }
        page.purchases.forEach { applyPurchase(it) }
        // Batches are derived by the server; its copy always wins (unconfirmed local batches have the same id).
        if (page.batches.isNotEmpty()) db.batches().upsertAll(page.batches.map { it.toEntity() })
        if (page.people.isNotEmpty()) {
            db.people().upsertAll(page.people.map { it.toEntity() })
            db.people().deleteOthers(page.people.map { it.id })
        }
    }

    /** If the owner removed a "view" permission, the server stops sending those records; remove local copies. */
    private suspend fun purgeHidden(perms: Set<Permission>) = db.withTransaction {
        if (Permission.SUPPLIER_VIEW !in perms) db.suppliers().deleteAll()
        if (Permission.ITEM_VIEW !in perms) db.items().deleteAll()
        val canViewLists = perms.any { it in setOf(Permission.LIST_CREATE, Permission.LIST_EDIT, Permission.LIST_DELETE, Permission.PURCHASE_RECORD) }
        if (!canViewLists) {
            db.lists().deleteAllLines()
            db.lists().deleteAll()
        }
        val canViewPurchases = perms.any {
            it in setOf(Permission.PURCHASE_HISTORY_VIEW, Permission.PURCHASE_RECORD, Permission.PURCHASE_EDIT, Permission.PURCHASE_DELETE)
        }
        if (Permission.EXPIRY_VIEW !in perms) db.batches().deleteAll()
        if (!canViewPurchases) {
            db.purchases().deleteEveryDeliveryLine()
            db.purchases().deleteEveryDelivery()
            db.purchases().deleteAllLines()
            db.purchases().deleteAll()
        }
    }

    private suspend fun hasLocalChanges(id: String) = db.outbox().countForEntity(id) > 0

    // ------------------------------------------------------------------------------------- conflicts

    /** Keep this phone's version: re-send it on top of the server's current version. */
    suspend fun keepMine(entityId: String) {
        db.withTransaction {
            val op = db.outbox().forEntity(entityId).firstOrNull { it.state == "CONFLICT" } ?: return@withTransaction
            setServerVersion(op.entityType, entityId, op.conflictVersion)
            setState(op.entityType, entityId, SyncState.PENDING, null)
            db.outbox().retryAsUpdate(op.opId, java.util.UUID.randomUUID().toString())
        }
        syncNow()
    }

    /** Take the other person's version and drop this phone's unsent changes to the record. */
    suspend fun useTheirs(entityId: String) {
        db.withTransaction {
            val op = db.outbox().forEntity(entityId).firstOrNull { it.state == "CONFLICT" } ?: return@withTransaction
            val copy = op.conflictJson
            db.outbox().deleteForEntity(entityId)
            if (copy != null) applyServerJson(op.entityType, copy) else removeLocal(op.entityType, entityId)
        }
    }

    /** Sends a change again that waited after repeated server errors. */
    suspend fun tryAgain(entityId: String) {
        db.withTransaction {
            db.outbox().retryAfterServerErrors(entityId)
            db.outbox().forEntity(entityId).firstOrNull()?.let { setState(it.entityType, entityId, SyncState.PENDING, null) }
        }
        syncNow()
    }

    /** Drop a refused change. The record goes back to the server's copy at the next full refresh. */
    suspend fun discard(entityId: String) {
        db.withTransaction {
            val op = db.outbox().forEntity(entityId).firstOrNull() ?: return@withTransaction
            db.outbox().deleteForEntity(entityId)
            if (serverVersion(op.entityType, entityId) == null) removeLocal(op.entityType, entityId)
            db.meta().put(LocalMeta(AppDatabase.META_SYNC_CURSOR, "0"))
        }
        syncNow()
    }

    // ------------------------------------------------------------------------------------- per type

    private suspend fun serverVersion(type: String, id: String): Long? = when (type) {
        EntityTypes.SUPPLIER -> db.suppliers().get(id)?.serverVersion
        EntityTypes.ITEM -> db.items().get(id)?.serverVersion
        EntityTypes.UNIT -> db.units().get(id)?.serverVersion
        EntityTypes.STOCK_LIST -> db.lists().get(id)?.serverVersion
        EntityTypes.PURCHASE -> db.purchases().get(id)?.serverVersion
        else -> null
    }

    private suspend fun setServerVersion(type: String, id: String, v: Long?) {
        when (type) {
            EntityTypes.SUPPLIER -> db.suppliers().get(id)?.let { db.suppliers().upsert(it.copy(serverVersion = v)) }
            EntityTypes.ITEM -> db.items().get(id)?.let { db.items().upsert(it.copy(serverVersion = v)) }
            EntityTypes.UNIT -> db.units().get(id)?.let { db.units().upsert(it.copy(serverVersion = v)) }
            EntityTypes.STOCK_LIST -> db.lists().get(id)?.let { db.lists().upsert(it.copy(serverVersion = v)) }
            EntityTypes.PURCHASE -> db.purchases().get(id)?.let { db.purchases().upsert(it.copy(serverVersion = v)) }
        }
    }

    private suspend fun setState(type: String, id: String, state: String, error: String?) {
        when (type) {
            EntityTypes.SUPPLIER -> db.suppliers().get(id)?.let { db.suppliers().upsert(it.copy(syncState = state, syncError = error)) }
            EntityTypes.ITEM -> db.items().get(id)?.let { db.items().upsert(it.copy(syncState = state, syncError = error)) }
            EntityTypes.UNIT -> db.units().get(id)?.let { db.units().upsert(it.copy(syncState = state, syncError = error)) }
            EntityTypes.STOCK_LIST -> db.lists().get(id)?.let { db.lists().upsert(it.copy(syncState = state, syncError = error)) }
            EntityTypes.PURCHASE -> db.purchases().get(id)?.let { db.purchases().upsert(it.copy(syncState = state, syncError = error)) }
            // A refused delivery shows on its purchase.
            EntityTypes.DELIVERY -> db.purchases().getDelivery(id)?.let { d ->
                db.purchases().get(d.purchaseId)?.let { db.purchases().upsert(it.copy(syncState = state, syncError = error)) }
            }
        }
    }

    private suspend fun applyServerCopy(type: String, id: String, r: PushResult) {
        when {
            r.supplier != null -> applySupplier(r.supplier)
            r.item != null -> applyItem(r.item)
            r.unit != null -> applyUnit(r.unit)
            r.stockList != null -> applyList(r.stockList)
            r.purchase != null -> {
                if (type == EntityTypes.DELIVERY) {
                    db.purchases().confirmDelivery(id)
                    db.purchases().confirmDeliveryLines(id)
                }
                applyPurchase(r.purchase)
            }
            else -> { setServerVersion(type, id, r.version); setState(type, id, SyncState.SYNCED, null) }
        }
    }

    private fun copyJson(r: PushResult): String? = when {
        r.supplier != null -> json.encodeToString(SupplierDto.serializer(), r.supplier)
        r.item != null -> json.encodeToString(ItemDto.serializer(), r.item)
        r.unit != null -> json.encodeToString(UnitDto.serializer(), r.unit)
        r.stockList != null -> json.encodeToString(StockListDto.serializer(), r.stockList)
        r.purchase != null -> json.encodeToString(PurchaseDto.serializer(), r.purchase)
        else -> null
    }

    private suspend fun applyServerJson(type: String, copy: String) {
        when (type) {
            EntityTypes.SUPPLIER -> applySupplier(json.decodeFromString(SupplierDto.serializer(), copy))
            EntityTypes.ITEM -> applyItem(json.decodeFromString(ItemDto.serializer(), copy))
            EntityTypes.UNIT -> applyUnit(json.decodeFromString(UnitDto.serializer(), copy))
            EntityTypes.STOCK_LIST -> applyList(json.decodeFromString(StockListDto.serializer(), copy))
            EntityTypes.PURCHASE -> applyPurchase(json.decodeFromString(PurchaseDto.serializer(), copy), force = true)
        }
    }

    private suspend fun removeLocal(type: String, id: String) {
        when (type) {
            EntityTypes.SUPPLIER -> db.suppliers().delete(id)
            EntityTypes.ITEM -> db.items().delete(id)
            EntityTypes.UNIT -> db.units().delete(id)
            EntityTypes.STOCK_LIST -> { db.lists().deleteLines(id); db.lists().delete(id) }
            EntityTypes.PURCHASE -> {
                db.batches().deleteForPurchase(id)
                db.purchases().deleteAllDeliveryLines(id); db.purchases().deleteAllDeliveries(id)
                db.purchases().deleteLines(id); db.purchases().delete(id)
            }
            EntityTypes.DELIVERY -> {
                // batches recorded with this delivery on the phone (their id is the receipt line id)
                val receipts = db.purchases().deliveryLinesOf(id).filter { it.kind == "RECEIPT" }.map { it.id }
                if (receipts.isNotEmpty()) db.batches().delete(receipts)
                db.purchases().deleteDeliveryLines(id); db.purchases().deleteDelivery(id)
            }
        }
    }

    private suspend fun applySupplier(d: SupplierDto) =
        if (d.deleted) db.suppliers().delete(d.id) else db.suppliers().upsert(d.toEntity())

    private suspend fun applyItem(d: ItemDto) =
        if (d.deleted) db.items().delete(d.id) else db.items().upsert(d.toEntity())

    private suspend fun applyUnit(d: UnitDto) =
        if (d.builtIn) Unit // built-in units ship with the app (BuiltInUnits)
        else if (d.deleted) db.units().delete(d.id) else db.units().upsert(d.toEntity())

    private suspend fun applyList(d: StockListDto) {
        db.lists().deleteLines(d.id)
        if (d.deleted) {
            db.lists().delete(d.id)
        } else {
            db.lists().upsert(d.toEntity())
            db.lists().insertLines(d.lineEntities())
        }
    }

    /**
     * Replaces the local purchase with the server's copy: header, lines and confirmed deliveries. Deliveries
     * recorded on this phone and not yet confirmed are kept. Skipped while the purchase itself has unsent edits
     * (their push decides), unless [force].
     */
    private suspend fun applyPurchase(d: PurchaseDto, force: Boolean = false) {
        if (!force && hasLocalChanges(d.id)) return
        // Deliveries in the server copy are confirmed now, even if this phone still marks them as unsent.
        d.deliveries.forEach { db.purchases().deleteDeliveryLines(it.id); db.purchases().deleteDelivery(it.id) }
        db.purchases().deleteConfirmedDeliveryLines(d.id)
        db.purchases().deleteConfirmedDeliveries(d.id)
        db.purchases().deleteLines(d.id)
        if (d.deleted) {
            db.purchases().deleteAllDeliveryLines(d.id)
            db.purchases().deleteAllDeliveries(d.id)
            db.purchases().delete(d.id)
            return
        }
        db.purchases().upsert(d.toEntity())
        db.purchases().insertLines(d.lineEntities())
        d.deliveryEntities().forEach { db.purchases().insertDelivery(it) }
        db.purchases().insertDeliveryLines(d.deliveryLineEntities())
    }

    companion object {
        private const val MAX_ROUNDS = 20
        private const val MAX_PAGES = 200
        /** Server errors on one change before it waits for the user ("Try again"). */
        const val MAX_SERVER_RETRIES = 5
        private const val META_PERMS = "sync_permissions"
        private const val META_EPOCH = "data_epoch"
    }
}
