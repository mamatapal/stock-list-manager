package app.stocklist.unit;

import app.stocklist.common.sync.SyncEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A unit such as KG or PCS. Built-in units have no business; custom units belong to one business. */
@Entity
@Table(name = "unit")
public class UnitOfMeasure extends SyncEntity {
    private String code;
    private String name;

    protected UnitOfMeasure() {}

    public UnitOfMeasure(UUID id, UUID businessId, UUID userId, String code, String name, Instant now, long seq) {
        initNew(id, businessId, userId, now, seq);
        this.code = code;
        this.name = name;
    }

    public void update(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public boolean isBuiltIn() { return businessId == null; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
