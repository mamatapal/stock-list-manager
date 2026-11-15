package app.stocklist.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sync state of a local record:
 * SYNCED   matches the server (serverVersion = server's version).
 * PENDING  changed here; an outbox operation will send it.
 * FAILED   the server refused the change (syncError says why).
 * CONFLICT someone else changed it first; the user chooses which version to keep.
 */
object SyncState {
    const val SYNCED = "SYNCED"
    const val PENDING = "PENDING"
    const val FAILED = "FAILED"
    const val CONFLICT = "CONFLICT"
}

@Entity(tableName = "suppliers", indices = [Index("name")])
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val contactPerson: String?,
    val mobile: String?,
    val altPhone: String?,
    val address: String?,
    val city: String?,
    val gstin: String?,
    val notes: String?,
    val active: Boolean,
    val deleted: Boolean = false,
    val serverVersion: Long?,
    val syncState: String,
    val syncError: String? = null,
    val updatedAt: Long,
    val createdBy: String? = null,
    val updatedBy: String? = null,
)

@Entity(tableName = "items", indices = [Index("name"), Index("category")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String?,
    val defaultUnitId: String,
    /** Decimal strings, never floating point. */
    val latestPurchasePrice: String?,
    val latestSellingPrice: String?,
    val lastPurchaseDate: String?,
    val preferredSupplierId: String?,
    val notes: String?,
    val active: Boolean,
    val deleted: Boolean = false,
    val serverVersion: Long?,
    val syncState: String,
    val syncError: String? = null,
    val updatedAt: Long,
    val createdBy: String? = null,
    val updatedBy: String? = null,
)

/** Custom units only; the 14 built-in units are in [BuiltInUnits]. */
@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val deleted: Boolean = false,
    val serverVersion: Long?,
    val syncState: String,
    val syncError: String? = null,
)

@Entity(tableName = "stock_lists", indices = [Index("listDate"), Index("supplierId")])
data class StockListEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** yyyy-MM-dd */
    val listDate: String,
    val supplierId: String?,
    val notes: String?,
    /** DRAFT or SAVED */
    val status: String,
    val receivingStatus: String,
    val totalAmount: String?,
    val createdBy: String?,
    val deleted: Boolean = false,
    val serverVersion: Long?,
    val syncState: String,
    val syncError: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
)

@Entity(tableName = "stock_list_lines", indices = [Index("listId")])
data class StockListLineEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val itemId: String?,
    val itemName: String,
    val description: String?,
    val quantity: String,
    val unitId: String,
    /** PER_UNIT or TOTAL */
    val priceBasis: String,
    val enteredPrice: String?,
    val lineTotal: String?,
    val sortOrder: Int,
    /** Received so far, as last confirmed by the server (local unsent deliveries are added on top for display). */
    @ColumnInfo(defaultValue = "0") val receivedQuantity: String = "0",
    /** A linked purchase line uses another unit and is not counted. */
    @ColumnInfo(defaultValue = "0") val unitMismatch: Boolean = false,
)

/**
 * One local change waiting to be sent. opId is the idempotency key the server remembers.
 * state: PENDING (to send), FAILED (refused, see lastError), CONFLICT (server copy in conflictJson).
 */
@Entity(tableName = "outbox", indices = [Index("entityId"), Index("createdAt")])
data class OutboxEntity(
    @PrimaryKey val opId: String,
    val entityType: String,
    val entityId: String,
    /** UPSERT or DELETE */
    val action: String,
    /** True when this creates the record on the server (no base version yet). */
    val isCreate: Boolean,
    val payloadJson: String?,
    val createdAt: Long,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val lastError: String? = null,
    val conflictJson: String? = null,
    val conflictVersion: Long? = null,
    /** Times the server answered RETRY (busy or a server error). After 5 the change waits for the user. */
    @ColumnInfo(defaultValue = "0") val serverRetries: Int = 0,
)

/** A supplier bill. Header and lines are synced as one record with one version. */
@Entity(tableName = "purchases", indices = [Index("purchaseDate"), Index("supplierId"), Index("stockListId")])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val supplierId: String,
    val supplierName: String?,
    val invoiceNo: String?,
    /** yyyy-MM-dd */
    val purchaseDate: String,
    val receivedDate: String?,
    val stockListId: String?,
    val notes: String?,
    /** DRAFT, PENDING, PARTIAL, RECEIVED, CANCELLED */
    val status: String,
    val overReceived: Boolean = false,
    val duplicateInvoice: Boolean = false,
    val itemCount: Int,
    val revisionNo: Int = 0,
    val totalCost: String?,
    /** Profit figures are null when this user may not see them (the server never sends them). */
    val totalExpectedSales: String?,
    val totalExpectedProfit: String?,
    val marginPercent: String?,
    val markupPercent: String?,
    val cancelReason: String? = null,
    val createdBy: String?,
    val deleted: Boolean = false,
    val serverVersion: Long?,
    val syncState: String,
    val syncError: String? = null,
    val updatedAt: Long,
    val updatedBy: String? = null,
    val completedBy: String? = null,
)

/** A billed line with its price snapshot. */
@Entity(tableName = "purchase_lines", indices = [Index("purchaseId"), Index("stockListItemId")])
data class PurchaseLineEntity(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val itemId: String,
    val itemName: String,
    val unitId: String,
    val unitCode: String,
    val quantity: String,
    val priceBasis: String,
    val purchasePrice: String?,
    val purchaseUnitPrice: String?,
    val sellingPrice: String?,
    val totalCost: String?,
    val expectedSales: String?,
    val expectedProfit: String?,
    val marginPercent: String?,
    val markupPercent: String?,
    val stockListItemId: String?,
    val updateCatalog: Boolean,
    val sortOrder: Int,
)

/** Goods arriving. pending = recorded on this phone and not yet confirmed by the server. */
@Entity(tableName = "deliveries", indices = [Index("purchaseId")])
data class DeliveryEntity(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val receivedDate: String,
    val receivedBy: String?,
    val notes: String?,
    val pending: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "delivery_lines", indices = [Index("deliveryId"), Index("purchaseLineId"), Index("purchaseId")])
data class DeliveryLineEntity(
    @PrimaryKey val id: String,
    val deliveryId: String,
    val purchaseId: String,
    val purchaseLineId: String,
    /** RECEIPT or CORRECTION */
    val kind: String,
    /** Signed for corrections. */
    val quantity: String,
    val correctsId: String?,
    val reason: String?,
    val batchNo: String?,
    val expiryDate: String?,
    val pending: Boolean,
)

/**
 * Received stock, for batch tracking and expiry. The id is the receipt line it came from. Item name and unit are the
 * purchase line's snapshots. pending = recorded on this phone, not yet confirmed by the server.
 */
@Entity(tableName = "batches", indices = [Index("expiryDate"), Index("itemId"), Index("purchaseId")])
data class BatchEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val itemName: String,
    val purchaseId: String,
    val purchaseLineId: String,
    val deliveryId: String?,
    val supplierId: String,
    val supplierName: String?,
    val invoiceNo: String?,
    val purchaseDate: String,
    val receivedDate: String,
    val receivedBy: String?,
    val batchNo: String?,
    /** The original receipt. */
    val receivedQuantity: String,
    /** What is left: the receipt plus its corrections. */
    val quantity: String,
    val unitCode: String,
    /** yyyy-MM-dd, or null when the goods have no expiry date. */
    val expiryDate: String?,
    val cancelled: Boolean,
    val pending: Boolean,
)

/** Names of the owner and staff, for "created by" labels. */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val role: String,
    val active: Boolean,
)
