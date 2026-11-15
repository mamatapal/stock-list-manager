package app.stocklist.app.data.repository

import androidx.room.withTransaction
import app.stocklist.app.data.local.AppDatabase
import app.stocklist.app.data.local.ItemEntity
import app.stocklist.app.data.local.StockListEntity
import app.stocklist.app.data.local.StockListLineEntity
import app.stocklist.app.data.local.SupplierEntity
import app.stocklist.app.data.local.SyncState
import app.stocklist.app.data.local.UnitEntity
import app.stocklist.app.data.remote.ItemInput
import app.stocklist.app.data.remote.StockListInput
import app.stocklist.app.data.remote.SupplierInput
import app.stocklist.app.data.remote.UnitInput
import app.stocklist.app.data.sync.EntityTypes
import app.stocklist.app.data.sync.Outbox
import app.stocklist.app.data.sync.SyncScheduler
import app.stocklist.app.data.sync.toInput
import app.stocklist.app.domain.catalogue.BuiltInUnits
import app.stocklist.app.domain.catalogue.Decimals
import app.stocklist.app.domain.catalogue.ListCalculator
import app.stocklist.app.domain.catalogue.UnitOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every change is written to Room together with an outbox entry in one transaction, then a sync is requested.
 * Screens read Room only, so they work the same with or without a connection.
 */
@Singleton
class SupplierRepository @Inject constructor(
    private val db: AppDatabase,
    private val outbox: Outbox,
    private val json: Json,
    private val scheduler: SyncScheduler,
) {
    fun observe(q: String, includeInactive: Boolean): Flow<List<SupplierEntity>> = db.suppliers().observe(q.trim(), includeInactive)
    fun observeOne(id: String): Flow<SupplierEntity?> = db.suppliers().observeOne(id)

    /** Creates (id = null) or updates a supplier. Returns its id. */
    suspend fun save(id: String?, input: SupplierInput): String {
        val newId = id ?: UUID.randomUUID().toString()
        db.withTransaction {
            val existing = db.suppliers().get(newId)
            db.suppliers().upsert(
                SupplierEntity(newId, input.name, input.contactPerson, input.mobile, input.altPhone, input.address,
                    input.city, input.gstin, input.notes, input.active, false, existing?.serverVersion,
                    SyncState.PENDING, null, System.currentTimeMillis(), existing?.createdBy, existing?.updatedBy),
            )
            outbox.enqueue(EntityTypes.SUPPLIER, newId, "UPSERT", existing?.serverVersion == null,
                json.encodeToString(SupplierInput.serializer(), input))
        }
        scheduler.requestNow()
        return newId
    }

    suspend fun delete(id: String) {
        deleteRecord(db, outbox, EntityTypes.SUPPLIER, id, { db.suppliers().get(it)?.serverVersion },
            { db.suppliers().delete(it) }, { db.suppliers().get(it)?.let { e -> db.suppliers().upsert(e.copy(deleted = true, syncState = SyncState.PENDING)) } })
        scheduler.requestNow()
    }
}

@Singleton
class ItemRepository @Inject constructor(
    private val db: AppDatabase,
    private val outbox: Outbox,
    private val json: Json,
    private val scheduler: SyncScheduler,
) {
    fun observe(q: String, category: String, includeInactive: Boolean): Flow<List<ItemEntity>> =
        db.items().observe(q.trim(), category, includeInactive)
    fun categories(): Flow<List<String>> = db.items().categories()
    fun observeOne(id: String): Flow<ItemEntity?> = db.items().observeOne(id)

    suspend fun nameInUse(name: String, exceptId: String?): Boolean =
        db.items().countWithName(name.trim(), exceptId ?: "") > 0

    suspend fun save(id: String?, input: ItemInput): String {
        val newId = id ?: UUID.randomUUID().toString()
        db.withTransaction {
            val existing = db.items().get(newId)
            db.items().upsert(
                ItemEntity(newId, input.name, input.category, input.defaultUnitId, input.latestPurchasePrice,
                    input.latestSellingPrice, existing?.lastPurchaseDate, input.preferredSupplierId, input.notes,
                    input.active, false, existing?.serverVersion, SyncState.PENDING, null, System.currentTimeMillis(),
                    existing?.createdBy, existing?.updatedBy),
            )
            outbox.enqueue(EntityTypes.ITEM, newId, "UPSERT", existing?.serverVersion == null,
                json.encodeToString(ItemInput.serializer(), input))
        }
        scheduler.requestNow()
        return newId
    }

    suspend fun delete(id: String) {
        deleteRecord(db, outbox, EntityTypes.ITEM, id, { db.items().get(it)?.serverVersion },
            { db.items().delete(it) }, { db.items().get(it)?.let { e -> db.items().upsert(e.copy(deleted = true, syncState = SyncState.PENDING)) } })
        scheduler.requestNow()
    }
}

@Singleton
class UnitRepository @Inject constructor(
    private val db: AppDatabase,
    private val outbox: Outbox,
    private val json: Json,
    private val scheduler: SyncScheduler,
) {
    /** Built-in units first, then this business's custom units. */
    fun observe(): Flow<List<UnitOption>> = db.units().observeCustom().map { custom ->
        BuiltInUnits.all + custom.map { UnitOption(it.id, it.code, it.name, false) }
    }

    suspend fun create(code: String, name: String): String {
        val c = code.trim().uppercase()
        require(c.isNotEmpty() && Regex("^[A-Z0-9 ._-]{1,20}$").matches(c)) { "Code: 1-20 letters or digits" }
        require(name.isNotBlank()) { "Name is required" }
        require(BuiltInUnits.all.none { it.code == c } && db.units().countWithCode(c) == 0) { "A unit with this code already exists" }
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            db.units().upsert(UnitEntity(id, c, name.trim(), false, null, SyncState.PENDING))
            outbox.enqueue(EntityTypes.UNIT, id, "UPSERT", true, json.encodeToString(UnitInput.serializer(), UnitInput(c, name.trim())))
        }
        scheduler.requestNow()
        return id
    }
}

data class ListLineDraft(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String? = null,
    val itemName: String = "",
    val description: String = "",
    val quantity: String = "",
    val unitId: String = BuiltInUnits.PCS,
    val priceBasis: String = ListCalculator.PER_UNIT,
    val enteredPrice: String = "",
)

@Singleton
class StockListRepository @Inject constructor(
    private val db: AppDatabase,
    private val outbox: Outbox,
    private val json: Json,
    private val scheduler: SyncScheduler,
) {
    fun observe(q: String, status: String, supplierId: String, from: String, to: String): Flow<List<StockListEntity>> =
        db.lists().observe(q.trim(), status, supplierId, from, to)

    fun observeOne(id: String): Flow<Pair<StockListEntity, List<StockListLineEntity>>?> =
        db.lists().observeOne(id).combine(db.lists().observeLines(id)) { list, lines -> list?.let { it to lines } }

    suspend fun lines(id: String) = db.lists().lines(id)
    suspend fun get(id: String) = db.lists().get(id)

    /**
     * Saves the list and its lines locally with totals calculated by the same rules as the server; the server
     * recalculates on arrival. Throws IllegalArgumentException with a readable message when something is invalid.
     */
    suspend fun save(
        id: String?, title: String, listDate: LocalDate, supplierId: String?, notes: String, status: String,
        lines: List<ListLineDraft>, createdBy: String?,
    ): String {
        require(title.isNotBlank()) { "Enter a list title" }
        require(status == "DRAFT" || lines.isNotEmpty()) { "Add at least one item, or save it as a draft" }
        val newId = id ?: UUID.randomUUID().toString()
        // Keep what the server last confirmed as received, so the screen does not flash back to zero.
        val before = db.lists().lines(newId).associateBy { it.id }
        val removed = before.keys - lines.map { it.id }.toSet()
        if (removed.isNotEmpty()) {
            val linked = db.purchases().linkedListLines(removed.toList()).toSet()
            before.values.firstOrNull { it.id in linked }?.let {
                throw IllegalArgumentException("${it.itemName} is linked to a purchase, so it cannot be removed from the list")
            }
        }
        val lineEntities = lines.mapIndexed { i, l ->
            val name = l.itemName.trim()
            require(name.isNotEmpty()) { "Line ${i + 1}: enter an item name" }
            val qty = Decimals.parse(l.quantity, "$name quantity", 3, false)
                ?: throw IllegalArgumentException("$name: enter a quantity")
            val price = Decimals.parse(l.enteredPrice, "$name price", ListCalculator.priceScale(l.priceBasis), true)
            StockListLineEntity(l.id, newId, l.itemId, name, l.description.trim().ifEmpty { null },
                Decimals.format(qty)!!, l.unitId, l.priceBasis, Decimals.format(price),
                Decimals.money(ListCalculator.lineTotal(l.priceBasis, qty, price)), i,
                before[l.id]?.receivedQuantity ?: "0", before[l.id]?.unitMismatch ?: false)
        }
        val total = ListCalculator.listTotal(lineEntities.map { it.lineTotal?.toBigDecimal() })
        db.withTransaction {
            val existing = db.lists().get(newId)
            val entity = StockListEntity(newId, title.trim(), listDate.toString(), supplierId, notes.trim().ifEmpty { null },
                status, existing?.receivingStatus ?: "PENDING", Decimals.money(total), existing?.createdBy ?: createdBy,
                false, existing?.serverVersion, SyncState.PENDING, null, System.currentTimeMillis(), createdBy)
            db.lists().upsert(entity)
            db.lists().deleteLines(newId)
            db.lists().insertLines(lineEntities)
            outbox.enqueue(EntityTypes.STOCK_LIST, newId, "UPSERT", existing?.serverVersion == null,
                json.encodeToString(StockListInput.serializer(), entity.toInput(lineEntities)))
        }
        scheduler.requestNow()
        return newId
    }

    /** Copies a list into a new draft dated today (works offline). */
    suspend fun duplicate(sourceId: String, createdBy: String?): String {
        val src = db.lists().get(sourceId) ?: throw IllegalArgumentException("List not found")
        val lines = db.lists().lines(sourceId).map {
            ListLineDraft(UUID.randomUUID().toString(), it.itemId, it.itemName, it.description.orEmpty(), it.quantity,
                it.unitId, it.priceBasis, it.enteredPrice.orEmpty())
        }
        return save(null, "Copy of ${src.title}", LocalDate.now(), src.supplierId, src.notes.orEmpty(), "DRAFT", lines, createdBy)
    }

    suspend fun delete(id: String) {
        deleteRecord(db, outbox, EntityTypes.STOCK_LIST, id, { db.lists().get(it)?.serverVersion },
            { db.lists().deleteLines(it); db.lists().delete(it) },
            { db.lists().get(it)?.let { e -> db.lists().upsert(e.copy(deleted = true, syncState = SyncState.PENDING)) } })
        scheduler.requestNow()
    }
}

/**
 * A record that never reached the server is simply removed with its queued changes. Otherwise it is hidden
 * locally and a DELETE is queued (the server keeps it as a soft-deleted record).
 */
private suspend fun deleteRecord(
    db: AppDatabase, outbox: Outbox, type: String, id: String,
    serverVersion: suspend (String) -> Long?, removeLocal: suspend (String) -> Unit, hideLocal: suspend (String) -> Unit,
) {
    db.withTransaction {
        if (serverVersion(id) == null) {
            db.outbox().deleteForEntity(id)
            removeLocal(id)
        } else {
            hideLocal(id)
            outbox.enqueue(type, id, "DELETE", false, null)
        }
    }
}
