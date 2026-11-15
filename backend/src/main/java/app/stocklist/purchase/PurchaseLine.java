package app.stocklist.purchase;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * One billed item with its price snapshot: item name, unit code and all prices are copied when the line is
 * saved. Nothing outside PurchaseService writes to this table, so catalogue edits never change history.
 */
@Entity
@Table(name = "purchase_item")
public class PurchaseLine implements Persistable<UUID> {
    @Id
    private UUID id;
    private UUID businessId;
    private UUID purchaseId;
    private UUID itemId;
    private String itemNameSnapshot;
    private UUID unitId;
    private String unitCodeSnapshot;
    private BigDecimal quantity;
    private String priceBasis;
    private BigDecimal enteredAmount;
    private BigDecimal purchaseUnitPrice;
    private BigDecimal sellingUnitPrice;
    private BigDecimal totalCost;
    private BigDecimal expectedSales;
    private BigDecimal expectedProfit;
    private BigDecimal marginPercent;
    private BigDecimal markupPercent;
    private UUID stockListItemId;
    private boolean updateCatalog;
    private int sortOrder;
    @Transient
    private boolean fresh = true;

    protected PurchaseLine() {}

    public PurchaseLine(UUID id, UUID businessId, UUID purchaseId) {
        this.id = id;
        this.businessId = businessId;
        this.purchaseId = purchaseId;
    }

    void apply(UUID itemId, String itemName, UUID unitId, String unitCode, BigDecimal quantity, String priceBasis,
               BigDecimal enteredAmount, BigDecimal sellingUnitPrice, PurchaseCalculator.Line calc,
               UUID stockListItemId, boolean updateCatalog, int sortOrder) {
        this.itemId = itemId;
        this.itemNameSnapshot = itemName;
        this.unitId = unitId;
        this.unitCodeSnapshot = unitCode;
        this.quantity = quantity;
        this.priceBasis = priceBasis;
        this.enteredAmount = enteredAmount;
        this.purchaseUnitPrice = calc.unitPrice();
        this.sellingUnitPrice = sellingUnitPrice;
        this.totalCost = calc.cost();
        this.expectedSales = calc.sales();
        this.expectedProfit = calc.profit();
        this.marginPercent = calc.margin();
        this.markupPercent = calc.markup();
        this.stockListItemId = stockListItemId;
        this.updateCatalog = updateCatalog;
        this.sortOrder = sortOrder;
    }

    void attachTo(UUID purchaseId) {
        this.purchaseId = purchaseId;
    }

    PurchaseCalculator.Line calc() {
        return new PurchaseCalculator.Line(totalCost, purchaseUnitPrice, expectedSales, expectedProfit, marginPercent,
                markupPercent);
    }

    @PostLoad
    @PostPersist
    void stored() { fresh = false; }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    public UUID getBusinessId() { return businessId; }
    public UUID getPurchaseId() { return purchaseId; }
    public UUID getItemId() { return itemId; }
    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public UUID getUnitId() { return unitId; }
    public String getUnitCodeSnapshot() { return unitCodeSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public String getPriceBasis() { return priceBasis; }
    public BigDecimal getEnteredAmount() { return enteredAmount; }
    public BigDecimal getPurchaseUnitPrice() { return purchaseUnitPrice; }
    public BigDecimal getSellingUnitPrice() { return sellingUnitPrice; }
    public BigDecimal getTotalCost() { return totalCost; }
    public BigDecimal getExpectedSales() { return expectedSales; }
    public BigDecimal getExpectedProfit() { return expectedProfit; }
    public BigDecimal getMarginPercent() { return marginPercent; }
    public BigDecimal getMarkupPercent() { return markupPercent; }
    public UUID getStockListItemId() { return stockListItemId; }
    public boolean isUpdateCatalog() { return updateCatalog; }
    public int getSortOrder() { return sortOrder; }
}
