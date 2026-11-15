package app.stocklist.purchase;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Received stock for expiry tracking (Phase 5 screens). quantity = the receipt plus its corrections. */
@Entity
@Table(name = "batch")
public class Batch implements Persistable<UUID> {
    @Id
    private UUID id;
    private UUID businessId;
    private UUID itemId;
    private UUID supplierId;
    private UUID purchaseId;
    private UUID purchaseItemId;
    private UUID deliveryItemId;
    private String batchNo;
    private BigDecimal quantity;
    private UUID unitId;
    private LocalDate purchaseDate;
    private LocalDate receivedDate;
    private LocalDate expiryDate;
    /** Pull order for phones; bumped when the batch or its purchase changes. */
    private long changeSeq;
    @Transient
    private boolean fresh = true;

    protected Batch() {}

    public Batch(UUID id, UUID businessId, UUID itemId, UUID supplierId, UUID purchaseId, UUID purchaseItemId,
                 UUID deliveryItemId, String batchNo, BigDecimal quantity, UUID unitId, LocalDate purchaseDate,
                 LocalDate receivedDate, LocalDate expiryDate, long changeSeq) {
        this.purchaseItemId = purchaseItemId;
        this.changeSeq = changeSeq;
        this.id = id;
        this.businessId = businessId;
        this.itemId = itemId;
        this.supplierId = supplierId;
        this.purchaseId = purchaseId;
        this.deliveryItemId = deliveryItemId;
        this.batchNo = batchNo;
        this.quantity = quantity;
        this.unitId = unitId;
        this.purchaseDate = purchaseDate;
        this.receivedDate = receivedDate;
        this.expiryDate = expiryDate;
    }

    void adjust(BigDecimal delta, long seq) {
        this.quantity = this.quantity.add(delta);
        this.changeSeq = seq;
    }

    @PostLoad
    @PostPersist
    void stored() { fresh = false; }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    public UUID getBusinessId() { return businessId; }
    public UUID getItemId() { return itemId; }
    public UUID getDeliveryItemId() { return deliveryItemId; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getQuantity() { return quantity; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseId() { return purchaseId; }
    public UUID getPurchaseItemId() { return purchaseItemId; }
    public UUID getUnitId() { return unitId; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public long getChangeSeq() { return changeSeq; }
    public LocalDate getExpiryDate() { return expiryDate; }
}
