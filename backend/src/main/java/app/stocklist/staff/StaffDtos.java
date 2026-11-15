package app.stocklist.staff;

import app.stocklist.common.util.Patterns;
import app.stocklist.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StaffDtos {
    private StaffDtos() {}

    public record StaffResponse(UUID id, String username, String fullName, String mobile, String status,
                                boolean mustChangePassword, Instant createdAt, Instant lastLoginAt) {
        public static StaffResponse of(AppUser u) {
            return new StaffResponse(u.getId(), u.getUsername(), u.getFullName(), u.getMobile(), u.getStatus().name(),
                    u.isMustChangePassword(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record CreateStaffRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Pattern(regexp = Patterns.USERNAME, message = "3-40 letters, digits, dots or underscores") String username,
            @Pattern(regexp = Patterns.MOBILE, message = "10-digit Indian mobile number") String mobile,
            /* Optional; when absent the staff defaults are granted. */
            List<@NotBlank String> permissions) {}

    /** The temporary password is shown once; the staff member must change it at first sign-in. */
    public record CreateStaffResponse(StaffResponse staff, List<String> permissions, String temporaryPassword) {}

    public record UpdateStaffRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = Patterns.MOBILE, message = "10-digit Indian mobile number") String mobile) {}

    public record PermissionsResponse(UUID userId, List<String> permissions) {}

    public record SetPermissionsRequest(@NotNull List<@NotBlank String> permissions) {}

    public record TemporaryPasswordResponse(String temporaryPassword) {}

    public record SessionInfo(UUID familyId, String deviceId, String deviceName, Instant signedInAt,
                              Instant expiresAt) {}
}
