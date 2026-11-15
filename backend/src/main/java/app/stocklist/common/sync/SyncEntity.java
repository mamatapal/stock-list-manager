package app.stocklist.common.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Columns every synchronised business record has. The id is created on the phone (so offline records have
 * their final id), version is incremented by the server on every change, change_seq orders changes for pull,
 * and deleted_at is a soft delete that also tells other phones to remove the record.
 */
@MappedSuperclass
public abstract class SyncEntity {
    @Id
    protected UUID id;
    @Column(name = "business_id")
    protected UUID businessId;
    @Version
    protected Long version;
    protected long changeSeq;
    protected UUID createdBy;
    protected UUID updatedBy;
    protected Instant createdAt;
    protected Instant updatedAt;
    protected Instant deletedAt;

    protected void initNew(UUID id, UUID businessId, UUID userId, Instant now, long seq) {
        this.id = id;
        this.businessId = businessId;
        this.createdBy = userId;
        this.updatedBy = userId;
        this.createdAt = now;
        this.updatedAt = now;
        this.changeSeq = seq;
    }

    public void touch(UUID userId, Instant now, long seq) {
        this.updatedBy = userId;
        this.updatedAt = now;
        this.changeSeq = seq;
    }

    public void softDelete(UUID userId, Instant now, long seq) {
        this.deletedAt = now;
        touch(userId, now, seq);
    }

    /**
     * "Keep mine" after a record was deleted on another phone: the edit is applied on top of the deletion (the phone
     * has seen that version), which brings the record back.
     */
    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() { return deletedAt != null; }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public long getVersion() { return version == null ? 0 : version; }
    public long getChangeSeq() { return changeSeq; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
