package app.stocklist.business;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Per-business change counter; used by synchronisation from Phase 2. */
@Entity
@Table(name = "business_seq")
public class BusinessSeq implements Persistable<UUID> {
    @Id
    private UUID businessId;
    private long nextChangeSeq;

    protected BusinessSeq() {}

    public BusinessSeq(UUID businessId) {
        this.businessId = businessId;
        this.nextChangeSeq = 1;
    }

    /** Returns the current number and advances the counter (caller holds the row lock). */
    public long take() {
        return nextChangeSeq++;
    }

    @Override
    public UUID getId() { return businessId; }

    @jakarta.persistence.Transient
    private boolean fresh;

    public static BusinessSeq create(UUID businessId) {
        BusinessSeq s = new BusinessSeq(businessId);
        s.fresh = true;
        return s;
    }

    @Override
    public boolean isNew() { return fresh; }
}
