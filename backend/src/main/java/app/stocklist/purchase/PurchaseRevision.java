package app.stocklist.purchase;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** The complete previous state of a completed purchase (header, lines, prices), kept before each change. */
@Entity
@Table(name = "purchase_revision")
public class PurchaseRevision implements Persistable<UUID> {
    @Id
    private UUID id;
    private UUID businessId;
    private UUID purchaseId;
    private int revisionNo;
    private String snapshot;
    private UUID changedBy;
    private Instant changedAt;
    private String reason;
    @Transient
    private boolean fresh = true;

    protected PurchaseRevision() {}

    public PurchaseRevision(UUID businessId, UUID purchaseId, int revisionNo, String snapshot, UUID changedBy,
                            Instant changedAt, String reason) {
        this.id = UUID.randomUUID();
        this.businessId = businessId;
        this.purchaseId = purchaseId;
        this.revisionNo = revisionNo;
        this.snapshot = snapshot;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.reason = reason;
    }

    @PostLoad
    @PostPersist
    void stored() { fresh = false; }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return fresh; }
    public UUID getPurchaseId() { return purchaseId; }
    public int getRevisionNo() { return revisionNo; }
    public String getSnapshot() { return snapshot; }
    public UUID getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public String getReason() { return reason; }
}
