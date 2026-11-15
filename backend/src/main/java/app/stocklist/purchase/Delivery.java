package app.stocklist.purchase;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Goods arriving against a purchase. Insert-only: never updated or deleted. */
@Entity
@Table(name = "purchase_delivery")
public class Delivery implements Persistable<UUID> {
    @Id
    private UUID id;
    private UUID businessId;
    private UUID purchaseId;
    private LocalDate receivedDate;
    private UUID receivedBy;
    private String notes;
    private Instant createdAt;
    @Transient
    private boolean fresh = true;

    protected Delivery() {}

    public Delivery(UUID id, UUID businessId, UUID purchaseId, LocalDate receivedDate, UUID receivedBy, String notes,
                    Instant createdAt) {
        this.id = id;
        this.businessId = businessId;
        this.purchaseId = purchaseId;
        this.receivedDate = receivedDate;
        this.receivedBy = receivedBy;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    @PostLoad
    @PostPersist
    void stored() { fresh = false; }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    public UUID getBusinessId() { return businessId; }
    public UUID getPurchaseId() { return purchaseId; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public UUID getReceivedBy() { return receivedBy; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
