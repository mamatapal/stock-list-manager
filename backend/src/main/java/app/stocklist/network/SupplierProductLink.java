package app.stocklist.network;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The shopkeeper's explicit decision that a supplier product is one of the shop's items, bought in a shop unit, with
 * unitsPerPack shop units in one supplier unit (e.g. 1 box = 24 pcs). Only ever created or changed by a shopkeeper's
 * request; names, barcodes and descriptions are never compared.
 */
@Entity
@Table(name = "supplier_product_link")
public class SupplierProductLink {
    @Id
    private UUID id;
    @Version
    private Long version;
    private UUID businessId;
    private UUID supplierOrgId;
    private UUID supplierProductId;
    private UUID itemId;
    private UUID unitId;
    private BigDecimal unitsPerPack;
    private UUID createdBy;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected SupplierProductLink() {}

    public SupplierProductLink(UUID id, UUID businessId, UUID supplierOrgId, UUID supplierProductId, UUID by,
                               Instant now) {
        this.id = id;
        this.businessId = businessId;
        this.supplierOrgId = supplierOrgId;
        this.supplierProductId = supplierProductId;
        this.createdBy = by;
        this.createdAt = now;
    }

    public void apply(UUID itemId, UUID unitId, BigDecimal unitsPerPack, UUID by, Instant now) {
        this.itemId = itemId;
        this.unitId = unitId;
        this.unitsPerPack = unitsPerPack;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public UUID getBusinessId() { return businessId; }
    public UUID getSupplierOrgId() { return supplierOrgId; }
    public UUID getSupplierProductId() { return supplierProductId; }
    public UUID getItemId() { return itemId; }
    public UUID getUnitId() { return unitId; }
    public BigDecimal getUnitsPerPack() { return unitsPerPack; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
