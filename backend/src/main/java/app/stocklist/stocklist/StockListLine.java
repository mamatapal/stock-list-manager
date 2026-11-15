package app.stocklist.stocklist;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** One requested item on a list. Saved and versioned together with its list. */
@Entity
@Table(name = "stock_list_item")
public class StockListLine implements Persistable<UUID> {
    @Id
    private UUID id;
    private UUID businessId;
    private UUID stockListId;
    private UUID itemId;
    private String itemName;
    private String description;
    private BigDecimal quantity;
    private UUID unitId;
    private String priceBasis;
    private BigDecimal enteredPrice;
    private BigDecimal lineTotal;
    private int sortOrder;
    @Transient
    private boolean fresh = true;

    protected StockListLine() {}

    public StockListLine(UUID id, UUID businessId, UUID stockListId) {
        this.id = id;
        this.businessId = businessId;
        this.stockListId = stockListId;
    }

    public void apply(UUID itemId, String itemName, String description, BigDecimal quantity, UUID unitId,
                      String priceBasis, BigDecimal enteredPrice, BigDecimal lineTotal, int sortOrder) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.description = description;
        this.quantity = quantity;
        this.unitId = unitId;
        this.priceBasis = priceBasis;
        this.enteredPrice = enteredPrice;
        this.lineTotal = lineTotal;
        this.sortOrder = sortOrder;
    }

    void attachTo(UUID listId) {
        this.stockListId = listId;
    }

    @PostLoad
    @PostPersist
    void stored() { fresh = false; }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    public UUID getBusinessId() { return businessId; }
    public UUID getStockListId() { return stockListId; }
    public UUID getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public UUID getUnitId() { return unitId; }
    public String getPriceBasis() { return priceBasis; }
    public BigDecimal getEnteredPrice() { return enteredPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public int getSortOrder() { return sortOrder; }
}
