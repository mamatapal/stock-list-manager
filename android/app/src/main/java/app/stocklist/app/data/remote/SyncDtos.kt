package app.stocklist.app.data.remote

import kotlinx.serialization.Serializable

// Payloads sent to the server (money and quantities are decimal strings) -------------------------

@Serializable
data class SupplierInput(
    val name: String,
    val contactPerson: String? = null,
    val mobile: String? = null,
    val altPhone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val gstin: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
)

@Serializable
data class ItemInput(
    val name: String,
    val category: String? = null,
    val defaultUnitId: String,
    val latestPurchasePrice: String? = null,
    val latestSellingPrice: String? = null,
    val preferredSupplierId: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
)

@Serializable data class UnitInput(val code: String, val name: String)

@Serializable
data class LineInput(
    val id: String,
    val itemId: String? = null,
    val itemName: String,
    val description: String? = null,
    val quantity: String,
    val unitId: String,
    val priceBasis: String,
    val enteredPrice: String? = null,
)

@Serializable
data class StockListInput(
    val title: String,
    val listDate: String,
    val supplierId: String? = null,
    val notes: String? = null,
    val status: String,
    val lines: List<LineInput>,
)

@Serializable
data class PurchaseLineInput(
    val id: String,
    val itemId: String,
    val quantity: String,
    val unitId: String,
    val priceBasis: String,
    val purchasePrice: String? = null,
    val sellingPrice: String? = null,
    val stockListItemId: String? = null,
    val updateCatalog: Boolean = true,
)

@Serializable
data class DeliveryLineInput(
    val id: String,
    val purchaseLineId: String,
    val kind: String,
    val quantity: String,
    val correctsId: String? = null,
    val reason: String? = null,
    val batchNo: String? = null,
    val expiryDate: String? = null,
)

@Serializable
data class DeliveryInput(
    val id: String,
    val purchaseId: String? = null,
    val receivedDate: String,
    val notes: String? = null,
    val lines: List<DeliveryLineInput>,
)

@Serializable
data class PurchaseInput(
    val supplierId: String,
    val invoiceNo: String? = null,
    val purchaseDate: String,
    val stockListId: String? = null,
    val notes: String? = null,
    val complete: Boolean,
    val lines: List<PurchaseLineInput>,
    /** Goods that came with the bill, recorded in the same server transaction. */
    val receive: DeliveryInput? = null,
)

// Records as the server returns them -----------------------------------------------------------

@Serializable
data class SupplierDto(
    val id: String, val name: String, val contactPerson: String? = null, val mobile: String? = null,
    val altPhone: String? = null, val address: String? = null, val city: String? = null, val gstin: String? = null,
    val notes: String? = null, val active: Boolean, val version: Long, val deleted: Boolean,
    val createdBy: String? = null, val updatedBy: String? = null,
)

@Serializable
data class ItemDto(
    val id: String, val name: String, val category: String? = null, val defaultUnitId: String,
    val latestPurchasePrice: String? = null, val latestSellingPrice: String? = null,
    val lastPurchaseDate: String? = null, val preferredSupplierId: String? = null, val notes: String? = null,
    val active: Boolean, val version: Long, val deleted: Boolean,
    val createdBy: String? = null, val updatedBy: String? = null,
)

@Serializable
data class UnitDto(val id: String, val code: String, val name: String, val builtIn: Boolean, val version: Long, val deleted: Boolean)

@Serializable
data class LineDto(
    val id: String, val itemId: String? = null, val itemName: String, val description: String? = null,
    val quantity: String, val unitId: String, val priceBasis: String, val enteredPrice: String? = null,
    val lineTotal: String? = null, val effectiveUnitPrice: String? = null, val sortOrder: Int,
    val receivedQuantity: String = "0", val remainingQuantity: String? = null, val overBy: String? = null,
    val receivingStatus: String = "PENDING", val unitMismatch: Boolean = false,
)

@Serializable
data class StockListDto(
    val id: String, val title: String, val listDate: String, val supplierId: String? = null,
    val notes: String? = null, val status: String, val receivingStatus: String, val totalAmount: String? = null,
    val lines: List<LineDto>, val version: Long, val deleted: Boolean, val createdBy: String? = null,
    val updatedBy: String? = null,
)

@Serializable
data class PurchaseLineDto(
    val id: String, val itemId: String, val itemName: String, val unitId: String, val unitCode: String,
    val quantity: String, val priceBasis: String, val purchasePrice: String? = null,
    val purchaseUnitPrice: String? = null, val sellingPrice: String? = null, val totalCost: String? = null,
    val expectedSales: String? = null, val expectedProfit: String? = null, val marginPercent: String? = null,
    val markupPercent: String? = null, val stockListItemId: String? = null, val updateCatalog: Boolean = true,
    val sortOrder: Int,
)

@Serializable
data class DeliveryLineDto(
    val id: String, val purchaseLineId: String, val kind: String, val quantity: String,
    val correctsId: String? = null, val reason: String? = null, val batchNo: String? = null,
    val expiryDate: String? = null,
)

@Serializable
data class DeliveryDto(
    val id: String, val receivedDate: String, val receivedBy: String? = null, val notes: String? = null,
    val lines: List<DeliveryLineDto>,
)

@Serializable
data class PurchaseDto(
    val id: String, val supplierId: String, val supplierName: String? = null, val invoiceNo: String? = null,
    val purchaseDate: String, val receivedDate: String? = null, val stockListId: String? = null,
    val notes: String? = null, val status: String, val overReceived: Boolean = false,
    val duplicateInvoice: Boolean = false, val itemCount: Int = 0, val revisionNo: Int = 0,
    val totalCost: String? = null, val totalExpectedSales: String? = null, val totalExpectedProfit: String? = null,
    val marginPercent: String? = null, val markupPercent: String? = null, val cancelReason: String? = null,
    val lines: List<PurchaseLineDto>, val deliveries: List<DeliveryDto> = emptyList(), val version: Long,
    val deleted: Boolean, val createdBy: String? = null, val updatedBy: String? = null,
    val completedBy: String? = null, val completedAt: String? = null, val updatedAt: String? = null,
)

@Serializable
data class BatchDto(
    val id: String, val itemId: String, val itemName: String? = null, val purchaseId: String,
    val purchaseLineId: String, val deliveryId: String? = null, val supplierId: String,
    val supplierName: String? = null, val invoiceNo: String? = null, val purchaseDate: String,
    val purchaseStatus: String, val receivedDate: String, val receivedBy: String? = null, val batchNo: String? = null,
    val receivedQuantity: String? = null, val quantity: String, val unitCode: String? = null,
    val expiryDate: String? = null, val cancelled: Boolean,
)

@Serializable
data class PersonDto(val id: String, val fullName: String, val role: String, val active: Boolean)

@Serializable
data class RevisionDto(
    val revisionNo: Int, val changedBy: String? = null, val changedAt: String, val reason: String? = null,
    val before: PurchaseDto,
)

// Sync protocol ---------------------------------------------------------------------------------

@Serializable
data class PushOp(
    val opId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val baseVersion: Long?,
    val supplier: SupplierInput? = null,
    val item: ItemInput? = null,
    val unit: UnitInput? = null,
    val stockList: StockListInput? = null,
    val purchase: PurchaseInput? = null,
    val delivery: DeliveryInput? = null,
    /** Reason for cancelling a completed purchase (action DELETE). */
    val reason: String? = null,
)

@Serializable data class PushRequest(val ops: List<PushOp>)

@Serializable
data class PushResult(
    val opId: String? = null,
    val status: String,
    val code: String? = null,
    val message: String? = null,
    val version: Long? = null,
    val supplier: SupplierDto? = null,
    val item: ItemDto? = null,
    val unit: UnitDto? = null,
    val stockList: StockListDto? = null,
    val purchase: PurchaseDto? = null,
)

@Serializable data class PushResponse(val results: List<PushResult>)

@Serializable
data class PullResponse(
    val suppliers: List<SupplierDto>,
    val items: List<ItemDto>,
    val units: List<UnitDto>,
    val stockLists: List<StockListDto>,
    val purchases: List<PurchaseDto> = emptyList(),
    val batches: List<BatchDto> = emptyList(),
    val people: List<PersonDto> = emptyList(),
    val nextSince: Long,
    val hasMore: Boolean,
    /** Changes only when the server's database was restored from a backup. */
    val dataEpoch: String? = null,
)
