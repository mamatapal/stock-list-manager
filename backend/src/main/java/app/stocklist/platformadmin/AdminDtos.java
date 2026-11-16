package app.stocklist.platformadmin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record LoginRequest(String username, String password, String code) {}

    public record AdminMe(UUID id, String username, String fullName, Instant sessionExpiresAt) {}

    public record ReasonRequest(String reason) {}

    /** One row of the supplier list. */
    public record SupplierSummary(UUID id, String legalName, String tradeName, String city, String state,
                                  String email, String mobile, String gstin, String status, Instant createdAt,
                                  Instant submittedAt, Instant reviewedAt, long version) {}

    public record SupplierEvent(String action, String actorType, String actorName, String fromStatus,
                                String toStatus, String reason, String ip, Instant at) {}

    public record SupplierUserInfo(UUID id, String email, String fullName, String mobile, String role,
                                   boolean emailVerified, Instant lastLoginAt) {}

    /** Everything an administrator needs to review a supplier, including warnings about possible duplicates. */
    public record SupplierDetail(SupplierSummary summary, String contactPerson, String address, String pinCode,
                                 String statusReason, Instant emailVerifiedAt, List<SupplierUserInfo> users,
                                 long otherSuppliersWithSameGstin, long otherSuppliersWithSameMobile,
                                 boolean emailVerifiedByReset, List<SupplierEvent> history) {}
}
