package app.stocklist.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Numbers travel as decimal strings ("12.50"), never as floating point. */
public final class PurchaseDtos {
    private PurchaseDtos() {}

    /**
     * One billed line. purchasePrice is per unit (PER_UNIT) or the amount for the whole line (TOTAL, at most 2
     * decimals). sellingPrice is always per unit. updateCatalog: copy these prices to the item's latest prices.
     */
    public record LineInput(
            @NotNull UUID id,
            @NotNull(message = "Choose an item") UUID itemId,
            @NotBlank(message = "Quantity is required") @Size(max = 20) String quantity,
            @NotNull(message = "Unit is required") UUID unitId,
            @NotBlank @Pattern(regexp = "^(PER_UNIT|TOTAL)$", message = "PER_UNIT or TOTAL") String priceBasis,
            @Size(max = 20) String purchasePrice,
            @Size(max = 20) String sellingPrice,
            UUID stockListItemId,
            Boolean updateCatalog) {}

    /**
     * complete = false keeps it a draft; true completes it (a completed purchase cannot go back to draft).
     * receive (optional) records goods that arrived with the bill, in the same transaction.
     */
    public record PurchaseInput(
            @NotNull(message = "Choose a supplier") UUID supplierId,
            @Size(max = 60) String invoiceNo,
            @NotNull(message = "Purchase date is required") LocalDate purchaseDate,
            UUID stockListId,
            @Size(max = 2000) String notes,
            boolean complete,
            @NotNull @Size(max = 500, message = "At most 500 items per purchase") List<@Valid @NotNull LineInput> lines,
            @Valid DeliveryInput receive) {}

    /**
     * RECEIPT: quantity > 0, optional batch number and expiry date.
     * CORRECTION: signed quantity (e.g. "-10") fixing an earlier receipt (correctsId), with a reason.
     */
    public record DeliveryLineInput(
            @NotNull UUID id,
            @NotNull UUID purchaseLineId,
            @NotBlank @Pattern(regexp = "^(RECEIPT|CORRECTION)$", message = "RECEIPT or CORRECTION") String kind,
            @NotBlank(message = "Quantity is required") @Size(max = 21) String quantity,
            UUID correctsId,
            @Size(max = 300) String reason,
            @Size(max = 60) String batchNo,
            LocalDate expiryDate) {}

    /** id is created by the phone, so the same delivery sent twice is recorded once. */
    public record DeliveryInput(
            @NotNull UUID id,
            UUID purchaseId,
            @NotNull(message = "Received date is required") LocalDate receivedDate,
            @Size(max = 1000) String notes,
            @NotNull @Size(min = 1, max = 500, message = "Enter at least one received quantity")
            List<@Valid @NotNull DeliveryLineInput> lines) {}

    public record CancelInput(@NotBlank(message = "Give a reason") @Size(max = 500) String reason) {}

    public record LineDto(UUID id, UUID itemId, String itemName, UUID unitId, String unitCode, String quantity,
                          String priceBasis, String purchasePrice, String purchaseUnitPrice, String sellingPrice,
                          String totalCost, String expectedSales, String expectedProfit, String marginPercent,
                          String markupPercent, boolean sellingBelowCost, UUID stockListItemId,
                          boolean updateCatalog, int sortOrder, String receivedQuantity, String remainingQuantity,
                          String overBy, String receivingStatus) {
        LineDto withoutProfit() {
            return new LineDto(id, itemId, itemName, unitId, unitCode, quantity, priceBasis, purchasePrice,
                    purchaseUnitPrice, sellingPrice, totalCost, null, null, null, null, sellingBelowCost,
                    stockListItemId, updateCatalog, sortOrder, receivedQuantity, remainingQuantity, overBy,
                    receivingStatus);
        }
    }

    public record DeliveryLineDto(UUID id, UUID purchaseLineId, String kind, String quantity, UUID correctsId,
                                  String reason, String batchNo, LocalDate expiryDate) {}

    public record DeliveryDto(UUID id, LocalDate receivedDate, UUID receivedBy, String notes, Instant createdAt,
                              List<DeliveryLineDto> lines) {}

    public record PurchaseDto(UUID id, UUID supplierId, String supplierName, String invoiceNo, LocalDate purchaseDate,
                              LocalDate receivedDate, UUID stockListId, String notes, String status,
                              boolean overReceived, boolean duplicateInvoice, int itemCount, int revisionNo,
                              String totalCost, String totalExpectedSales, String totalExpectedProfit,
                              String marginPercent, String markupPercent, String cancelReason,
                              List<LineDto> lines, List<DeliveryDto> deliveries, long version, boolean deleted,
                              UUID createdBy, Instant completedAt, Instant createdAt, Instant updatedAt,
                              UUID updatedBy, UUID completedBy, Instant cancelledAt) {
        /** For users without EXPECTED_PROFIT_VIEW: expected sales, profit, margin and markup are removed. */
        public PurchaseDto withoutProfit() {
            return new PurchaseDto(id, supplierId, supplierName, invoiceNo, purchaseDate, receivedDate, stockListId,
                    notes, status, overReceived, duplicateInvoice, itemCount, revisionNo, totalCost, null, null, null,
                    null, cancelReason, lines.stream().map(LineDto::withoutProfit).toList(), deliveries, version,
                    deleted, createdBy, completedAt, createdAt, updatedAt, updatedBy, completedBy, cancelledAt);
        }
    }

    public record RevisionDto(int revisionNo, UUID changedBy, Instant changedAt, String reason, PurchaseDto before) {}
}
