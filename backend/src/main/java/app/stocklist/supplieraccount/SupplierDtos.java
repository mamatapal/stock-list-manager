package app.stocklist.supplieraccount;

import java.time.Instant;
import java.util.UUID;

/**
 * Requests and answers of the supplier portal API. Inputs are checked in {@link SupplierInputs}, which answers with
 * {@link SupplierPortalError} codes, so the Supplier Management app can show every message in English or Hindi.
 */
public final class SupplierDtos {
    private SupplierDtos() {}

    /** Cleaned business details (see {@link SupplierInputs#details}). */
    public record BusinessDetails(String legalName, String tradeName, String contactPerson, String mobile,
                                  String address, String city, String state, String pinCode, String gstin) {}

    public record RegisterRequest(String legalName, String tradeName, String contactPerson, String mobile,
                                  String email, String password, String address, String city, String state,
                                  String pinCode, String gstin) {}

    public record ProfileRequest(String legalName, String tradeName, String contactPerson, String mobile,
                                 String address, String city, String state, String pinCode, String gstin) {}

    public record TokenRequest(String token) {}

    public record EmailRequest(String email) {}

    public record LoginRequest(String email, String password, String deviceId, String deviceName,
                               String appVersion) {}

    public record RefreshRequest(String refreshToken) {}

    public record ResetPasswordRequest(String token, String newPassword) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record UserDto(UUID id, String email, String fullName, String mobile, String role,
                          boolean emailVerified) {}

    public record OrgDto(UUID id, String legalName, String tradeName, String contactPerson, String mobile,
                         String email, String address, String city, String state, String pinCode, String gstin,
                         String status, String statusReason, Instant submittedAt, Instant reviewedAt,
                         boolean canEditDetails, long version) {}

    public record MeResponse(UserDto user, OrgDto org) {}

    public record SessionResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                                  Instant refreshTokenExpiresAt, MeResponse me) {}

    public record VerifyEmailResponse(String status) {}

    static UserDto user(SupplierUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getMobile(), u.getRole(), u.isEmailVerified());
    }

    static OrgDto org(SupplierOrg o) {
        return new OrgDto(o.getId(), o.getLegalName(), o.getTradeName(), o.getContactPerson(), o.getMobile(),
                o.getEmail(), o.getAddress(), o.getCity(), o.getState(), o.getPinCode(), o.getGstin(), o.getStatus(),
                o.getStatusReason(), o.getSubmittedAt(), o.getReviewedAt(),
                SupplierOrg.EDITABLE.contains(o.getStatus()), o.getVersion() == null ? 0 : o.getVersion());
    }
}
