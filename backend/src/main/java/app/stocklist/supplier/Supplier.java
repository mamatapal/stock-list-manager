package app.stocklist.supplier;

import app.stocklist.common.sync.SyncEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier")
public class Supplier extends SyncEntity {
    private String name;
    private String contactPerson;
    private String mobile;
    private String altPhone;
    private String address;
    private String city;
    private String gstin;
    private String notes;
    private boolean active;

    protected Supplier() {}

    public Supplier(UUID id, UUID businessId, UUID userId, Instant now, long seq) {
        initNew(id, businessId, userId, now, seq);
    }

    public void apply(SupplierDtos.SupplierInput in) {
        this.name = in.name().trim();
        this.contactPerson = blank(in.contactPerson());
        this.mobile = blank(in.mobile());
        this.altPhone = blank(in.altPhone());
        this.address = blank(in.address());
        this.city = blank(in.city());
        this.gstin = in.gstin() == null || in.gstin().isBlank() ? null : in.gstin().trim().toUpperCase();
        this.notes = blank(in.notes());
        this.active = in.active() == null || in.active();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String getName() { return name; }
    public String getContactPerson() { return contactPerson; }
    public String getMobile() { return mobile; }
    public String getAltPhone() { return altPhone; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getGstin() { return gstin; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
}
