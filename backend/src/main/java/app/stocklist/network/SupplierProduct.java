package app.stocklist.network;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One product in a supplier's catalogue. Separate from every shop's items: publishing or editing a product never
 * creates or changes a shop item. Products are deactivated, never deleted, so old orders keep their reference.
 */
@Entity
@Table(name = "supplier_product")
public class SupplierProduct {
    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID supplierOrgId;
    private String nameEn;
    private String nameHi;
    private String unit;
    private String pack;
    private BigDecimal price;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    protected SupplierProduct() {}

    public SupplierProduct(UUID id, UUID supplierOrgId, Instant now) {
        this.id = id;
        this.supplierOrgId = supplierOrgId;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void apply(String nameEn, String nameHi, String unit, String pack, BigDecimal price, Instant now) {
        this.nameEn = nameEn;
        this.nameHi = nameHi;
        this.unit = unit;
        this.pack = pack;
        this.price = price;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public String getNameEn() { return nameEn; }
    public String getNameHi() { return nameHi; }
    public String getUnit() { return unit; }
    public String getPack() { return pack; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
