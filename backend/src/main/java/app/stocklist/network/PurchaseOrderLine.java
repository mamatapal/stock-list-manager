package app.stocklist.network;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ordered product, frozen when the order is placed: the supplier's name, unit, pack and price at that time, and
 * the shop item, unit and conversion the shopkeeper had linked. Later catalogue or link changes never alter it; only
 * the received quantity and price are filled in, once, by the goods receipt.
 */
@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine {
    @Id
    private UUID id;
    private UUID businessId;
    private UUID supplierOrgId;
    private UUID orderId;
    private int lineNo;
    private UUID supplierProductId;
    private String productNameEn;
    private String productNameHi;
    private String unit;
    private String pack;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
    private UUID itemId;
    private UUID itemUnitId;
    private BigDecimal unitsPerPack;
    private BigDecimal receivedQuantity;
    private BigDecimal receivedUnitPrice;

    protected PurchaseOrderLine() {}

    public PurchaseOrderLine(UUID id, UUID businessId, UUID orderId, int lineNo, SupplierProduct product,
                             SupplierProductLink link, BigDecimal quantity, BigDecimal lineAmount) {
        this.id = id;
        this.businessId = businessId;
        this.supplierOrgId = product.getSupplierOrgId();
        this.orderId = orderId;
        this.lineNo = lineNo;
        this.supplierProductId = product.getId();
        this.productNameEn = product.getNameEn();
        this.productNameHi = product.getNameHi();
        this.unit = product.getUnit();
        this.pack = product.getPack();
        this.unitPrice = product.getPrice();
        this.quantity = quantity;
        this.lineAmount = lineAmount;
        this.itemId = link.getItemId();
        this.itemUnitId = link.getUnitId();
        this.unitsPerPack = link.getUnitsPerPack();
    }

    public void received(BigDecimal quantity, BigDecimal unitPrice) {
        this.receivedQuantity = quantity;
        this.receivedUnitPrice = unitPrice;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public UUID getOrderId() { return orderId; }
    public int getLineNo() { return lineNo; }
    public UUID getSupplierProductId() { return supplierProductId; }
    public String getProductNameEn() { return productNameEn; }
    public String getProductNameHi() { return productNameHi; }
    public String getUnit() { return unit; }
    public String getPack() { return pack; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public UUID getItemId() { return itemId; }
    public UUID getItemUnitId() { return itemUnitId; }
    public BigDecimal getUnitsPerPack() { return unitsPerPack; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getReceivedUnitPrice() { return receivedUnitPrice; }
}
