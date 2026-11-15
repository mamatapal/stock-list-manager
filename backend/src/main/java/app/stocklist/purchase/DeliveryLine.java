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

/** Quantity received for one purchase line. RECEIPT is positive; CORRECTION is signed and fixes a receipt. */
@Entity
@Table(name = "purchase_delivery_item")
public class DeliveryLine implements Persistable<UUID> {
    public static final String RECEIPT = "RECEIPT";
    public static final String CORRECTION = "CORRECTION";

    @Id
    private UUID id;
    private UUID businessId;
    private UUID deliveryId;
    private UUID purchaseItemId;
    private String kind;
    private BigDecimal quantityReceived;
    private UUID correctsId;
    private String reason;
    private String batchNo;
    private LocalDate expiryDate;
    @Transient
    private boolean fresh = true;

    protected DeliveryLine() {}

    public DeliveryLine(UUID id, UUID businessId, UUID deliveryId, UUID purchaseItemId, String kind,
                        BigDecimal quantityReceived, UUID correctsId, String reason, String batchNo,
                        LocalDate expiryDate) {
        this.id = id;
        this.businessId = businessId;
        this.deliveryId = deliveryId;
        this.purchaseItemId = purchaseItemId;
        this.kind = kind;
        this.quantityReceived = quantityReceived;
        this.correctsId = correctsId;
        this.reason = reason;
        this.batchNo = batchNo;
        this.expiryDate = expiryDate;
    }

    @PostLoad
    @PostPersist
    void stored() { fresh = false; }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    public UUID getBusinessId() { return businessId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getPurchaseItemId() { return purchaseItemId; }
    public String getKind() { return kind; }
    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public UUID getCorrectsId() { return correctsId; }
    public String getReason() { return reason; }
    public String getBatchNo() { return batchNo; }
    public LocalDate getExpiryDate() { return expiryDate; }
}
