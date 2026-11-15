package app.stocklist.item;

import app.stocklist.common.sync.Decimals;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ItemDtos {
    private ItemDtos() {}

    /** Prices are decimal strings, e.g. "140" or "12.75" (up to 4 decimal places). */
    public record ItemInput(
            @NotBlank(message = "Item name is required") @Size(max = 150) String name,
            @Size(max = 80) String category,
            @NotNull(message = "Default unit is required") UUID defaultUnitId,
            @Size(max = 20) String latestPurchasePrice,
            @Size(max = 20) String latestSellingPrice,
            UUID preferredSupplierId,
            @Size(max = 1000) String notes,
            Boolean active) {}

    public record ItemDto(UUID id, String name, String category, UUID defaultUnitId, String latestPurchasePrice,
                          String latestSellingPrice, LocalDate lastPurchaseDate, UUID preferredSupplierId,
                          String notes, boolean active, long version, boolean deleted, Instant createdAt,
                          Instant updatedAt, UUID createdBy, UUID updatedBy) {
        public static ItemDto of(Item i) {
            return new ItemDto(i.getId(), i.getName(), i.getCategory(), i.getDefaultUnitId(),
                    Decimals.format(i.getLatestPurchasePrice()), Decimals.format(i.getLatestSellingPrice()),
                    i.getLastPurchaseDate(), i.getPreferredSupplierId(), i.getNotes(), i.isActive(), i.getVersion(),
                    i.isDeleted(), i.getCreatedAt(), i.getUpdatedAt(), i.getCreatedBy(), i.getUpdatedBy());
        }
    }
}
