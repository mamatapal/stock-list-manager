package app.stocklist.stocklist;

import app.stocklist.common.sync.Decimals;
import app.stocklist.purchase.ReceivingCalculator;
import app.stocklist.purchase.ReceivingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StockListDtos {
    private StockListDtos() {}

    /**
     * One line. quantity and enteredPrice are decimal strings. priceBasis PER_UNIT: enteredPrice is the price of
     * one unit. priceBasis TOTAL: enteredPrice is the amount for the whole line (at most 2 decimals).
     */
    public record LineInput(
            @NotNull UUID id,
            UUID itemId,
            @Size(max = 150) String itemName,
            @Size(max = 500) String description,
            @NotBlank(message = "Quantity is required") @Size(max = 20) String quantity,
            @NotNull(message = "Unit is required") UUID unitId,
            @NotBlank @Pattern(regexp = "^(PER_UNIT|TOTAL)$", message = "PER_UNIT or TOTAL") String priceBasis,
            @Size(max = 20) String enteredPrice) {}

    public record StockListInput(
            @NotBlank(message = "List title is required") @Size(max = 150) String title,
            @NotNull(message = "Date is required") LocalDate listDate,
            UUID supplierId,
            @Size(max = 2000) String notes,
            @NotBlank @Pattern(regexp = "^(DRAFT|SAVED)$", message = "DRAFT or SAVED") String status,
            @NotNull @Size(max = 500, message = "At most 500 items per list") List<@Valid @NotNull LineInput> lines) {}

    /**
     * quantity is the requested quantity and never changes when goods arrive. receivedQuantity is the sum of all
     * deliveries on purchase lines linked to this line (same unit only); remainingQuantity never goes below zero,
     * an excess is overBy. unitMismatch: a linked purchase line uses another unit and is not counted.
     */
    public record LineDto(UUID id, UUID itemId, String itemName, String description, String quantity, UUID unitId,
                          String priceBasis, String enteredPrice, String lineTotal, String effectiveUnitPrice,
                          int sortOrder, String receivedQuantity, String remainingQuantity, String overBy,
                          String receivingStatus, boolean unitMismatch) {
        public static LineDto of(StockListLine l, ReceivingService.ListLineProgress p) {
            var pr = p == null ? ReceivingCalculator.progress(l.getQuantity(), null) : p.progress();
            return new LineDto(l.getId(), l.getItemId(), l.getItemName(), l.getDescription(),
                    Decimals.format(l.getQuantity()), l.getUnitId(), l.getPriceBasis(),
                    Decimals.format(l.getEnteredPrice()), Decimals.money(l.getLineTotal()),
                    Decimals.format(StockListCalculator.effectiveUnitPrice(l.getPriceBasis(), l.getQuantity(),
                            l.getEnteredPrice())),
                    l.getSortOrder(), Decimals.format(pr.received()), Decimals.format(pr.remaining()),
                    Decimals.format(pr.overBy()), pr.status(), p != null && p.unitMismatch());
        }
    }

    public record StockListDto(UUID id, String title, LocalDate listDate, UUID supplierId, String notes,
                               String status, String receivingStatus, String totalAmount, List<LineDto> lines,
                               long version, boolean deleted, UUID createdBy, Instant createdAt, Instant updatedAt,
                               UUID updatedBy) {
        public static StockListDto of(StockList s, List<StockListLine> lines,
                                      java.util.Map<UUID, ReceivingService.ListLineProgress> progress) {
            return new StockListDto(s.getId(), s.getTitle(), s.getListDate(), s.getSupplierId(), s.getNotes(),
                    s.getStatus(), s.getReceivingStatus(), Decimals.money(s.getTotalAmount()),
                    lines.stream().map(l -> LineDto.of(l, progress.get(l.getId()))).toList(), s.getVersion(), s.isDeleted(), s.getCreatedBy(),
                    s.getCreatedAt(), s.getUpdatedAt(), s.getUpdatedBy());
        }
    }
}
