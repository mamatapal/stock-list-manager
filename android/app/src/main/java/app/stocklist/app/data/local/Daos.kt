package app.stocklist.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE deleted = 0 AND (:includeInactive OR active = 1) AND (:q = '' OR name LIKE '%' || :q || '%' OR city LIKE '%' || :q || '%' OR contactPerson LIKE '%' || :q || '%' OR mobile LIKE '%' || :q || '%') ORDER BY name COLLATE NOCASE")
    fun observe(q: String, includeInactive: Boolean): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    fun observeOne(id: String): Flow<SupplierEntity?>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun get(id: String): SupplierEntity?

    @Upsert
    suspend fun upsert(e: SupplierEntity)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM suppliers")
    suspend fun deleteAll()
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deleted = 0 AND (:includeInactive OR active = 1) AND (:q = '' OR name LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') AND (:category = '' OR category = :category) ORDER BY name COLLATE NOCASE")
    fun observe(q: String, category: String, includeInactive: Boolean): Flow<List<ItemEntity>>

    @Query("SELECT DISTINCT category FROM items WHERE deleted = 0 AND category IS NOT NULL ORDER BY category COLLATE NOCASE")
    fun categories(): Flow<List<String>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeOne(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE deleted = 0 AND LOWER(name) = LOWER(:name) AND id <> :exceptId")
    suspend fun countWithName(name: String, exceptId: String): Int

    @Upsert
    suspend fun upsert(e: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units WHERE deleted = 0 ORDER BY code")
    fun observeCustom(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun get(id: String): UnitEntity?

    @Query("SELECT COUNT(*) FROM units WHERE deleted = 0 AND UPPER(code) = UPPER(:code)")
    suspend fun countWithCode(code: String): Int

    @Upsert
    suspend fun upsert(e: UnitEntity)

    @Query("DELETE FROM units WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface StockListDao {
    @Query("""SELECT * FROM stock_lists s WHERE deleted = 0
        AND (:status = '' OR status = :status)
        AND (:supplierId = '' OR supplierId = :supplierId)
        AND (:fromDate = '' OR listDate >= :fromDate) AND (:toDate = '' OR listDate <= :toDate)
        AND (:q = '' OR title LIKE '%' || :q || '%' OR notes LIKE '%' || :q || '%'
             OR EXISTS (SELECT 1 FROM stock_list_lines l WHERE l.listId = s.id AND l.itemName LIKE '%' || :q || '%'))
        ORDER BY listDate DESC, updatedAt DESC""")
    fun observe(q: String, status: String, supplierId: String, fromDate: String, toDate: String): Flow<List<StockListEntity>>

    @Query("SELECT * FROM stock_lists WHERE id = :id")
    fun observeOne(id: String): Flow<StockListEntity?>

    @Query("SELECT * FROM stock_lists WHERE id = :id")
    suspend fun get(id: String): StockListEntity?

    @Query("SELECT * FROM stock_list_lines WHERE listId = :listId ORDER BY sortOrder")
    fun observeLines(listId: String): Flow<List<StockListLineEntity>>

    @Query("SELECT * FROM stock_list_lines WHERE listId = :listId ORDER BY sortOrder")
    suspend fun lines(listId: String): List<StockListLineEntity>

    @Query("SELECT COUNT(*) FROM stock_list_lines WHERE listId = :listId")
    fun observeLineCount(listId: String): Flow<Int>

    @Upsert
    suspend fun upsert(e: StockListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<StockListLineEntity>)

    @Query("DELETE FROM stock_list_lines WHERE listId = :listId")
    suspend fun deleteLines(listId: String)

    @Query("DELETE FROM stock_lists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM stock_lists")
    suspend fun deleteAll()

    @Query("DELETE FROM stock_list_lines")
    suspend fun deleteAllLines()
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(op: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt, opId")
    suspend fun all(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE entityId = :entityId ORDER BY createdAt, opId")
    suspend fun forEntity(entityId: String): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE entityId = :entityId")
    suspend fun countForEntity(entityId: String): Int

    @Query("SELECT * FROM outbox WHERE state IN ('FAILED','CONFLICT') ORDER BY createdAt")
    fun observeProblems(): Flow<List<OutboxEntity>>

    @Query("SELECT COUNT(*) FROM outbox WHERE state = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("SELECT MAX(createdAt) FROM outbox")
    suspend fun lastCreatedAt(): Long?

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM outbox ORDER BY createdAt")
    fun observeAll(): Flow<List<OutboxEntity>>

    /** The server remembers every op id's outcome, so corrected content must go out under a new op id. */
    @Query("UPDATE outbox SET opId = :newOpId, payloadJson = :payload, action = :action, state = 'PENDING', lastError = NULL, attempts = 0 WHERE opId = :opId")
    suspend fun replacePayload(opId: String, newOpId: String, payload: String?, action: String)

    @Query("UPDATE outbox SET state = :state, lastError = :error, conflictJson = :conflictJson, conflictVersion = :conflictVersion, attempts = attempts + 1 WHERE opId = :opId")
    suspend fun mark(opId: String, state: String, error: String?, conflictJson: String?, conflictVersion: Long?)

    /** Re-sends a conflicted change on top of the server's version, under a new op id (see replacePayload). */
    @Query("UPDATE outbox SET opId = :newOpId, state = 'PENDING', isCreate = 0, conflictJson = NULL, conflictVersion = NULL, lastError = NULL WHERE opId = :opId")
    suspend fun retryAsUpdate(opId: String, newOpId: String)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE opId = :opId")
    suspend fun noteAttempt(opId: String, error: String)

    @Query("UPDATE outbox SET attempts = attempts + 1, serverRetries = serverRetries + 1, lastError = :error WHERE opId = :opId")
    suspend fun noteServerRetry(opId: String, error: String)

    @Query("SELECT * FROM outbox WHERE opId = :opId")
    suspend fun get(opId: String): OutboxEntity?

    /** "Try again" on a change that waited after repeated server errors. */
    @Query("UPDATE outbox SET state = 'PENDING', serverRetries = 0, lastError = NULL WHERE entityId = :entityId AND state = 'FAILED' AND serverRetries > 0")
    suspend fun retryAfterServerErrors(entityId: String): Int

    @Query("DELETE FROM outbox WHERE opId = :opId")
    suspend fun delete(opId: String)

    @Query("DELETE FROM outbox WHERE entityId = :entityId")
    suspend fun deleteForEntity(entityId: String)
}

@Dao
interface MetaFlowDao {
    @Query("SELECT value FROM local_meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>
}

@Dao
interface PurchaseDao {
    @Query("""SELECT p.* FROM purchases p WHERE deleted = 0
        AND (:status = '' OR status = :status)
        AND (:supplierId = '' OR supplierId = :supplierId)
        AND (:fromDate = '' OR purchaseDate >= :fromDate) AND (:toDate = '' OR purchaseDate <= :toDate)
        AND (:q = '' OR invoiceNo LIKE '%' || :q || '%' OR notes LIKE '%' || :q || '%'
             OR supplierName LIKE '%' || :q || '%'
             OR EXISTS (SELECT 1 FROM purchase_lines l WHERE l.purchaseId = p.id AND l.itemName LIKE '%' || :q || '%'))
        ORDER BY purchaseDate DESC, updatedAt DESC""")
    fun observe(q: String, status: String, supplierId: String, fromDate: String, toDate: String): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    fun observeOne(id: String): Flow<PurchaseEntity?>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun get(id: String): PurchaseEntity?

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY sortOrder")
    fun observeLines(purchaseId: String): Flow<List<PurchaseLineEntity>>

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY sortOrder")
    suspend fun lines(purchaseId: String): List<PurchaseLineEntity>

    @Query("SELECT * FROM purchase_lines WHERE stockListItemId IN (:listLineIds)")
    fun observeLinesForListLines(listLineIds: List<String>): Flow<List<PurchaseLineEntity>>

    @Query("SELECT * FROM deliveries WHERE purchaseId = :purchaseId ORDER BY receivedDate, createdAt")
    fun observeDeliveries(purchaseId: String): Flow<List<DeliveryEntity>>

    @Query("SELECT * FROM delivery_lines WHERE purchaseId = :purchaseId")
    fun observeDeliveryLines(purchaseId: String): Flow<List<DeliveryLineEntity>>

    @Query("SELECT * FROM delivery_lines WHERE purchaseId = :purchaseId")
    suspend fun deliveryLines(purchaseId: String): List<DeliveryLineEntity>

    /** Unconfirmed deliveries, which the server's figures do not include yet. */
    @Query("SELECT * FROM delivery_lines WHERE pending = 1")
    fun observePendingDeliveryLines(): Flow<List<DeliveryLineEntity>>

    @Query("SELECT * FROM purchase_lines WHERE id IN (:ids)")
    suspend fun linesById(ids: List<String>): List<PurchaseLineEntity>

    @Query("SELECT * FROM purchase_lines")
    fun observeAllLines(): Flow<List<PurchaseLineEntity>>

    /** Item-wise history: every billed line of one item (drafts excluded, cancelled included), newest first. */
    @Query("""SELECT p.id AS purchaseId, l.id AS purchaseLineId, p.purchaseDate, p.status, p.supplierId, p.supplierName,
        p.invoiceNo, l.itemName, l.quantity, l.unitId, l.unitCode, l.priceBasis, l.purchasePrice, l.purchaseUnitPrice,
        l.sellingPrice, l.totalCost
        FROM purchase_lines l JOIN purchases p ON p.id = l.purchaseId
        WHERE l.itemId = :itemId AND p.deleted = 0 AND p.status <> 'DRAFT'
        AND (:fromDate = '' OR p.purchaseDate >= :fromDate) AND (:toDate = '' OR p.purchaseDate <= :toDate)
        ORDER BY p.purchaseDate DESC, p.updatedAt DESC""")
    fun observeItemHistory(itemId: String, fromDate: String, toDate: String): Flow<List<ItemHistoryRow>>

    /** Supplier-wise history: drafts excluded, cancelled included (marked), newest first. */
    @Query("""SELECT * FROM purchases WHERE supplierId = :supplierId AND deleted = 0 AND status <> 'DRAFT'
        AND (:fromDate = '' OR purchaseDate >= :fromDate) AND (:toDate = '' OR purchaseDate <= :toDate)
        ORDER BY purchaseDate DESC, updatedAt DESC""")
    fun observeSupplierHistory(supplierId: String, fromDate: String, toDate: String): Flow<List<PurchaseEntity>>

    @Query("SELECT stockListItemId FROM purchase_lines WHERE stockListItemId IN (:listLineIds)")
    suspend fun linkedListLines(listLineIds: List<String>): List<String>

    @Upsert
    suspend fun upsert(e: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<PurchaseLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelivery(d: DeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveryLines(lines: List<DeliveryLineEntity>)

    @Query("DELETE FROM purchase_lines WHERE purchaseId = :purchaseId")
    suspend fun deleteLines(purchaseId: String)

    /** Server-confirmed deliveries are replaced by the server copy; unconfirmed ones stay until sent. */
    @Query("DELETE FROM deliveries WHERE purchaseId = :purchaseId AND pending = 0")
    suspend fun deleteConfirmedDeliveries(purchaseId: String)

    @Query("DELETE FROM delivery_lines WHERE purchaseId = :purchaseId AND pending = 0")
    suspend fun deleteConfirmedDeliveryLines(purchaseId: String)

    @Query("DELETE FROM deliveries WHERE id = :id")
    suspend fun deleteDelivery(id: String)

    @Query("DELETE FROM delivery_lines WHERE deliveryId = :id")
    suspend fun deleteDeliveryLines(id: String)

    @Query("SELECT * FROM delivery_lines WHERE deliveryId = :id")
    suspend fun deliveryLinesOf(id: String): List<DeliveryLineEntity>

    @Query("SELECT * FROM deliveries WHERE id = :id")
    suspend fun getDelivery(id: String): DeliveryEntity?

    @Query("UPDATE deliveries SET pending = 0 WHERE id = :id")
    suspend fun confirmDelivery(id: String)

    @Query("UPDATE delivery_lines SET pending = 0 WHERE deliveryId = :id")
    suspend fun confirmDeliveryLines(id: String)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM deliveries WHERE purchaseId = :purchaseId")
    suspend fun deleteAllDeliveries(purchaseId: String)

    @Query("DELETE FROM delivery_lines WHERE purchaseId = :purchaseId")
    suspend fun deleteAllDeliveryLines(purchaseId: String)

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()

    @Query("DELETE FROM purchase_lines")
    suspend fun deleteAllLines()

    @Query("DELETE FROM deliveries")
    suspend fun deleteEveryDelivery()

    @Query("DELETE FROM delivery_lines")
    suspend fun deleteEveryDeliveryLine()
}

/** One billed line of an item with its purchase, for item-wise history. */
data class ItemHistoryRow(
    val purchaseId: String,
    val purchaseLineId: String,
    val purchaseDate: String,
    val status: String,
    val supplierId: String,
    val supplierName: String?,
    val invoiceNo: String?,
    val itemName: String,
    val quantity: String,
    val unitId: String,
    val unitCode: String,
    val priceBasis: String,
    val purchasePrice: String?,
    val purchaseUnitPrice: String?,
    val sellingPrice: String?,
    val totalCost: String?,
)

@Dao
interface BatchDao {
    /**
     * Batch tracking (expiryList = false) or an expiry list (expiryList = true: only batches with an expiry date and
     * stock left, never cancelled). Dates are yyyy-MM-dd strings, so text comparison is date order.
     */
    @Query("""SELECT * FROM batches WHERE
        (:expiryList = 0 OR (expiryDate IS NOT NULL AND CAST(quantity AS REAL) > 0 AND cancelled = 0))
        AND (:includeCancelled = 1 OR cancelled = 0)
        AND (:expiryFrom = '' OR expiryDate >= :expiryFrom) AND (:expiryTo = '' OR expiryDate <= :expiryTo)
        AND (:itemId = '' OR itemId = :itemId)
        AND (:receivedFrom = '' OR receivedDate >= :receivedFrom) AND (:receivedTo = '' OR receivedDate <= :receivedTo)
        AND (:q = '' OR batchNo LIKE '%' || :q || '%' OR itemName LIKE '%' || :q || '%'
             OR supplierName LIKE '%' || :q || '%' OR invoiceNo LIKE '%' || :q || '%')
        ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate, receivedDate DESC""")
    fun observe(
        expiryList: Boolean, includeCancelled: Boolean, expiryFrom: String, expiryTo: String, itemId: String,
        receivedFrom: String, receivedTo: String, q: String,
    ): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    fun observeOne(id: String): Flow<BatchEntity?>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun get(id: String): BatchEntity?

    @Query("SELECT * FROM batches WHERE purchaseId = :purchaseId ORDER BY expiryDate")
    fun observeForPurchase(purchaseId: String): Flow<List<BatchEntity>>

    @Query("""SELECT COUNT(*) FROM batches WHERE expiryDate IS NOT NULL AND CAST(quantity AS REAL) > 0 AND cancelled = 0
        AND (:from = '' OR expiryDate >= :from) AND (:to = '' OR expiryDate <= :to)""")
    fun observeCount(from: String, to: String): Flow<Int>

    @Query("""SELECT COUNT(*) FROM batches WHERE expiryDate IS NOT NULL AND CAST(quantity AS REAL) > 0 AND cancelled = 0
        AND (:from = '' OR expiryDate >= :from) AND (:to = '' OR expiryDate <= :to)""")
    suspend fun count(from: String, to: String): Int

    @Upsert
    suspend fun upsert(b: BatchEntity)

    @Upsert
    suspend fun upsertAll(b: List<BatchEntity>)

    @Query("DELETE FROM batches WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE batches SET cancelled = 1 WHERE purchaseId = :purchaseId")
    suspend fun cancelForPurchase(purchaseId: String)

    @Query("DELETE FROM batches WHERE purchaseId = :purchaseId")
    suspend fun deleteForPurchase(purchaseId: String)

    @Query("DELETE FROM batches")
    suspend fun deleteAll()
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people")
    fun observeAll(): Flow<List<PersonEntity>>

    @Upsert
    suspend fun upsertAll(p: List<PersonEntity>)

    @Query("DELETE FROM people WHERE id NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)
}

/**
 * Used when the server's database has been restored from a backup: every downloaded record is dropped so the phone
 * can download the restored data again. Records with changes not yet sent (in the outbox) are kept and sent later.
 */
@Dao
interface ResyncDao {
    @Query("DELETE FROM suppliers WHERE id NOT IN (SELECT entityId FROM outbox)")
    suspend fun suppliers()

    @Query("DELETE FROM items WHERE id NOT IN (SELECT entityId FROM outbox)")
    suspend fun items()

    @Query("DELETE FROM units WHERE id NOT IN (SELECT entityId FROM outbox)")
    suspend fun units()

    @Query("DELETE FROM stock_list_lines WHERE listId NOT IN (SELECT entityId FROM outbox)")
    suspend fun listLines()

    @Query("DELETE FROM stock_lists WHERE id NOT IN (SELECT entityId FROM outbox)")
    suspend fun lists()

    @Query("""DELETE FROM delivery_lines WHERE deliveryId NOT IN (SELECT entityId FROM outbox)
        AND purchaseId NOT IN (SELECT entityId FROM outbox)""")
    suspend fun deliveryLines()

    @Query("""DELETE FROM deliveries WHERE id NOT IN (SELECT entityId FROM outbox)
        AND purchaseId NOT IN (SELECT entityId FROM outbox)""")
    suspend fun deliveries()

    @Query("""DELETE FROM purchase_lines WHERE purchaseId NOT IN (SELECT entityId FROM outbox)
        AND purchaseId NOT IN (SELECT purchaseId FROM deliveries)""")
    suspend fun purchaseLines()

    @Query("""DELETE FROM purchases WHERE id NOT IN (SELECT entityId FROM outbox)
        AND id NOT IN (SELECT purchaseId FROM deliveries)""")
    suspend fun purchases()

    @Query("DELETE FROM batches WHERE pending = 0")
    suspend fun batches()
}
