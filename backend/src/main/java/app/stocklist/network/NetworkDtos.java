package app.stocklist.network;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Requests and answers of the supplier network API. Numbers travel as decimal strings ("12.50"). Requests are
 * checked in the services (after the caller's permission and the record's ownership), so a refused caller never
 * learns anything from validation messages.
 */
public final class NetworkDtos {
    private NetworkDtos() {}

    // ------------------------------------------------------------------ requests (shop)

    /** supplierId (optional): one of the shop's own supplier contacts to link to this supplier. */
    public record ConnectionRequest(UUID supplierOrgId, UUID supplierId) {}

    /** Either an existing supplier contact of the shop, or createContact = true to add a new one. */
    public record ContactLinkInput(UUID supplierId, Boolean createContact) {}

    /** Optional (or, where stated, required) reason or note. */
    public record ReasonInput(String reason) {}

    /** A new shop item, created only because the shopkeeper asked for it while linking a product. */
    public record NewItemInput(String name, String category, UUID defaultUnitId) {}

    /**
     * The shopkeeper's link: an existing item (itemId) or a new one (newItem). unitId: the shop unit the goods are
     * counted in (default: the item's unit). unitsPerPack: shop units in one supplier unit (default 1).
     */
    public record LinkInput(UUID itemId, NewItemInput newItem, UUID unitId, String unitsPerPack) {}

    public record OrderLineInput(UUID productId, String quantity) {}

    /** id (optional): chosen by the app so that sending the same order twice places it once. */
    public record OrderInput(UUID id, UUID connectionId, String note, List<OrderLineInput> lines) {}

    /**
     * One received line: the quantity in the supplier's unit (0 to the ordered quantity). unitPrice (optional)
     * changes the saved order price and needs PURCHASE_PRICE_ENTER.
     */
    public record ReceiptLineInput(UUID lineId, String quantity, String unitPrice, String batchNo,
                                   LocalDate expiryDate) {}

    /** Every order line, once. receivedDate defaults to today in the business time zone. */
    public record ReceiptInput(LocalDate receivedDate, String invoiceNo, String notes,
                               List<ReceiptLineInput> lines) {}

    // ------------------------------------------------------------------ requests (supplier)

    public record ListingInput(Boolean listed) {}

    public record ProductInput(String nameEn, String nameHi, String unit, String pack, String price) {}

    // ------------------------------------------------------------------ answers

    /** What any shop may see about a supplier: never contact details, email, GSTIN or account data. */
    public record SupplierSummary(UUID id, String name, String legalName, String city, String state) {}

    /** What a supplier may see about a shop. */
    public record ShopSummary(String name, String city, String state) {}

    /** connectionStatus: this shop's connection with the supplier, or null. */
    public record DirectoryEntry(SupplierSummary supplier, String connectionStatus) {}

    /** actorId is the shop user or supplier user who acted; left out in answers to the other side. */
    /** receiptStatus: FULL or SHORT on an order's RECEIVED entry, otherwise null. */
    public record EventDto(String actorType, UUID actorId, String fromStatus, String toStatus, String reason,
                           String receiptStatus, Instant at) {}

    public record ShopConnectionDto(UUID id, String status, SupplierSummary supplier, UUID supplierContactId,
                                    Instant requestedAt, Instant respondedAt, Instant endedAt, String endedBy,
                                    String statusReason, long version, List<EventDto> history) {}

    public record SupplierConnectionDto(UUID id, String status, ShopSummary shop, Instant requestedAt,
                                        Instant respondedAt, Instant endedAt, String endedBy, String statusReason,
                                        long version, List<EventDto> history) {}

    public record ListingDto(boolean listed, String status) {}

    public record ProductDto(UUID id, String nameEn, String nameHi, String unit, String pack, String price,
                             boolean active, long version, Instant createdAt, Instant updatedAt) {}

    public record LinkDto(UUID id, UUID itemId, String itemName, UUID unitId, String unitsPerPack, long version,
                          Instant updatedAt) {}

    /** link: the shopkeeper's link to a shop item, or null while not linked (no automatic matching). */
    public record CatalogueEntry(ProductDto product, LinkDto link) {}

    public record CatalogueDto(SupplierSummary supplier, List<CatalogueEntry> products) {}

    /** item fields are the shop's own and are only in the shop's answers. */
    public record OrderLineDto(UUID id, int lineNo, UUID productId, String productNameEn, String productNameHi,
                               String unit, String pack, String quantity, String unitPrice, String lineAmount,
                               UUID itemId, UUID itemUnitId, String unitsPerPack, String receivedQuantity,
                               String receivedUnitPrice, String shortageQuantity, String receiptStatus) {}

    public record ShopOrderDto(UUID id, String status, SupplierSummary supplier, UUID connectionId, String note,
                               String totalAmount, int lineCount, UUID createdBy, Instant createdAt,
                               Instant updatedAt, Instant acceptedAt, Instant rejectedAt, Instant dispatchedAt,
                               Instant receivedAt, Instant cancelledAt, Instant deliveryFailedAt,
                               String statusReason, UUID purchaseId, String receiptStatus, long version,
                               List<OrderLineDto> lines,
                               List<EventDto> history) {}

    public record SupplierOrderDto(UUID id, String status, ShopSummary shop, String note, String totalAmount,
                                   int lineCount, Instant createdAt, Instant updatedAt, Instant acceptedAt,
                                   Instant rejectedAt, Instant dispatchedAt, Instant receivedAt, Instant cancelledAt,
                                   Instant deliveryFailedAt, String statusReason, String receiptStatus, long version,
                                   List<OrderLineDto> lines, List<EventDto> history) {}

    /** The received order and the purchase the receipt created (with the saved or changed order prices). */
    public record ReceiptDto(ShopOrderDto order, UUID purchaseId, String receiptStatus) {}
}
