package app.stocklist.supplier.data.remote

import kotlinx.serialization.Serializable

// Supplier Network requests and answers of the supplier portal (/api/v1/supplier-portal), mirroring the backend's
// NetworkDtos (see docs/API.md, "Supplier network"). Times are ISO-8601 strings; money and quantities are decimal
// strings exactly as the server sends them (never floating point).

@Serializable
data class ListingRequest(val listed: Boolean)

/** listed: shown in the shops' supplier directory. status: the business's review status (only APPROVED is shown). */
@Serializable
data class ListingDto(val listed: Boolean, val status: String)

@Serializable
data class ReasonRequest(val reason: String?)

/** What a supplier sees of a shop: name, city and state only (never phone or other details). */
@Serializable
data class ShopSummaryDto(val name: String, val city: String? = null, val state: String? = null)

/** One entry of a connection's or an order's history. receiptStatus: FULL or SHORT on an order's RECEIVED entry. */
@Serializable
data class EventDto(
    val actorType: String,
    val actorId: String? = null,
    val fromStatus: String? = null,
    val toStatus: String,
    val reason: String? = null,
    val receiptStatus: String? = null,
    val at: String,
)

@Serializable
data class ConnectionDto(
    val id: String,
    val status: String,
    val shop: ShopSummaryDto,
    val requestedAt: String? = null,
    val respondedAt: String? = null,
    val endedAt: String? = null,
    val endedBy: String? = null,
    val statusReason: String? = null,
    val version: Long = 0,
    val history: List<EventDto> = emptyList(),
)

@Serializable
data class ProductRequest(
    val nameEn: String,
    val nameHi: String?,
    val unit: String,
    val pack: String?,
    val price: String,
)

@Serializable
data class ProductDto(
    val id: String,
    val nameEn: String,
    val nameHi: String? = null,
    val unit: String,
    val pack: String? = null,
    val price: String,
    val active: Boolean,
    val version: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * One order line as the supplier sees it. The shop's own item fields (itemId and so on) are not sent to suppliers.
 * receivedQuantity, shortageQuantity and receiptStatus (FULL, SHORT, NOT_RECEIVED) appear once the shop has received
 * the goods.
 */
@Serializable
data class OrderLineDto(
    val id: String,
    val lineNo: Int,
    val productId: String? = null,
    val productNameEn: String,
    val productNameHi: String? = null,
    val unit: String,
    val pack: String? = null,
    val quantity: String,
    val unitPrice: String,
    val lineAmount: String,
    val receivedQuantity: String? = null,
    val receivedUnitPrice: String? = null,
    val shortageQuantity: String? = null,
    val receiptStatus: String? = null,
)

/** A purchase order from a connected shop. lines and history are filled only when one order is fetched. */
@Serializable
data class OrderDto(
    val id: String,
    val status: String,
    val shop: ShopSummaryDto,
    val note: String? = null,
    val totalAmount: String,
    val lineCount: Int,
    val createdAt: String,
    val updatedAt: String? = null,
    val acceptedAt: String? = null,
    val rejectedAt: String? = null,
    val dispatchedAt: String? = null,
    val receivedAt: String? = null,
    val cancelledAt: String? = null,
    val deliveryFailedAt: String? = null,
    val statusReason: String? = null,
    val receiptStatus: String? = null,
    val version: Long = 0,
    val lines: List<OrderLineDto> = emptyList(),
    val history: List<EventDto> = emptyList(),
)

/** Status values of connections and orders, as the server sends them. */
object ConnectionStatus {
    const val REQUESTED = "REQUESTED"
    const val ACTIVE = "ACTIVE"
    const val DECLINED = "DECLINED"
    const val CANCELLED = "CANCELLED"
    const val ENDED = "ENDED"
}

object OrderStatus {
    const val SENT = "SENT"
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val DISPATCHED = "DISPATCHED"
    const val RECEIVED = "RECEIVED"
    const val CANCELLED = "CANCELLED"
    const val DELIVERY_FAILED = "DELIVERY_FAILED"
}
