package app.stocklist.item;

import app.stocklist.common.sync.SyncEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "item")
public class Item extends SyncEntity {
    private String name;
    private String category;
    private UUID defaultUnitId;
    private BigDecimal latestPurchasePrice;
    private BigDecimal latestSellingPrice;
    /** Set by purchases (Phase 4); read-only here. */
    private LocalDate lastPurchaseDate;
    private UUID preferredSupplierId;
    private String notes;
    private boolean active;

    protected Item() {}

    public Item(UUID id, UUID businessId, UUID userId, Instant now, long seq) {
        initNew(id, businessId, userId, now, seq);
    }

    public void apply(String name, String category, UUID defaultUnitId, BigDecimal purchase, BigDecimal selling,
                      UUID preferredSupplierId, String notes, boolean active) {
        this.name = name;
        this.category = category;
        this.defaultUnitId = defaultUnitId;
        this.latestPurchasePrice = purchase;
        this.latestSellingPrice = selling;
        this.preferredSupplierId = preferredSupplierId;
        this.notes = notes;
        this.active = active;
    }

    /** Latest prices from a completed purchase. Returns true when something changed. */
    public boolean recordPurchase(BigDecimal purchase, BigDecimal selling, LocalDate date) {
        boolean changed = false;
        if (purchase != null && (latestPurchasePrice == null || latestPurchasePrice.compareTo(purchase) != 0)) {
            latestPurchasePrice = purchase;
            changed = true;
        }
        if (selling != null && (latestSellingPrice == null || latestSellingPrice.compareTo(selling) != 0)) {
            latestSellingPrice = selling;
            changed = true;
        }
        if (lastPurchaseDate == null || date.isAfter(lastPurchaseDate)) {
            lastPurchaseDate = date;
            changed = true;
        }
        return changed;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public UUID getDefaultUnitId() { return defaultUnitId; }
    public BigDecimal getLatestPurchasePrice() { return latestPurchasePrice; }
    public BigDecimal getLatestSellingPrice() { return latestSellingPrice; }
    public LocalDate getLastPurchaseDate() { return lastPurchaseDate; }
    public UUID getPreferredSupplierId() { return preferredSupplierId; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
}
