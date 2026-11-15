package app.stocklist.business;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business")
public class Business {
    public static final String ACTIVE = "ACTIVE";

    @Id
    private UUID id;
    @Version
    private Long version;
    private String name;
    private String address;
    private String city;
    private String state;
    private String pinCode;
    private String phone;
    private String email;
    private String gstin;
    private String logoKey;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    protected Business() {}

    public Business(BusinessDetails d, Instant now) {
        this.id = UUID.randomUUID();
        this.status = ACTIVE;
        this.createdAt = now;
        apply(d, now);
    }

    public void apply(BusinessDetails d, Instant now) {
        this.name = d.name();
        this.address = d.address();
        this.city = d.city();
        this.state = d.state();
        this.pinCode = d.pinCode();
        this.phone = d.phone();
        this.email = d.email();
        this.gstin = d.gstin();
        this.updatedAt = now;
    }

    public boolean isActive() { return ACTIVE.equals(status); }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPinCode() { return pinCode; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getGstin() { return gstin; }
    public String getLogoKey() { return logoKey; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
