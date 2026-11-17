package app.stocklist.supplieraccount;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A supplier business registered through the Supplier Management app. Not tied to any shop. */
@Entity
@Table(name = "supplier_org")
public class SupplierOrg {
    public static final String PENDING_EMAIL_VERIFICATION = "PENDING_EMAIL_VERIFICATION";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUSPENDED = "SUSPENDED";
    public static final Set<String> STATUSES = Set.of(PENDING_EMAIL_VERIFICATION, PENDING_APPROVAL, APPROVED,
            REJECTED, SUSPENDED);
    /** States in which the supplier may still change the business details. */
    public static final Set<String> EDITABLE = Set.of(PENDING_EMAIL_VERIFICATION, PENDING_APPROVAL, REJECTED);

    @Id
    private UUID id;
    @Version
    private Long version;
    private String legalName;
    private String tradeName;
    private String contactPerson;
    private String mobile;
    private String email;
    private String address;
    private String city;
    private String state;
    private String pinCode;
    private String gstin;
    private String status;
    private Instant emailVerifiedAt;
    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String statusReason;
    private Instant createdAt;
    private Instant updatedAt;
    /** Shown in shop discovery only when true and the organisation is APPROVED. Off until the supplier opts in. */
    private boolean listedInDirectory;

    protected SupplierOrg() {}

    public SupplierOrg(SupplierDtos.BusinessDetails d, String email, Instant now) {
        this.id = UUID.randomUUID();
        apply(d);
        this.email = email;
        this.status = PENDING_EMAIL_VERIFICATION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void apply(SupplierDtos.BusinessDetails d) {
        this.legalName = d.legalName();
        this.tradeName = d.tradeName();
        this.contactPerson = d.contactPerson();
        this.mobile = d.mobile();
        this.address = d.address();
        this.city = d.city();
        this.state = d.state();
        this.pinCode = d.pinCode();
        this.gstin = d.gstin();
    }

    public void emailVerified(Instant now) {
        if (emailVerifiedAt == null) emailVerifiedAt = now;
        if (PENDING_EMAIL_VERIFICATION.equals(status)) {
            status = PENDING_APPROVAL;
            submittedAt = now;
        }
        updatedAt = now;
    }

    /** A rejected supplier corrected the details and sends them for review again. */
    public void resubmit(Instant now) {
        status = PENDING_APPROVAL;
        submittedAt = now;
        statusReason = null;
        updatedAt = now;
    }

    public void review(String newStatus, String reason, UUID adminId, Instant now) {
        status = newStatus;
        statusReason = reason;
        reviewedBy = adminId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    public boolean isApproved() { return APPROVED.equals(status); }

    /** The supplier's own choice to appear in shop discovery (V7). */
    public void listInDirectory(boolean listed, Instant now) {
        this.listedInDirectory = listed;
        this.updatedAt = now;
    }

    public boolean isListedInDirectory() { return listedInDirectory; }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getContactPerson() { return contactPerson; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPinCode() { return pinCode; }
    public String getGstin() { return gstin; }
    public String getStatus() { return status; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getStatusReason() { return statusReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
