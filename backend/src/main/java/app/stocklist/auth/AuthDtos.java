package app.stocklist.auth;

import app.stocklist.common.util.Patterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterBusinessRequest(
            @NotBlank @Size(max = 150) String businessName,
            @NotBlank @Size(max = 120) String ownerName,
            @NotBlank @Pattern(regexp = Patterns.USERNAME, message = "3-40 letters, digits, dots or underscores") String username,
            @NotBlank @Pattern(regexp = Patterns.MOBILE, message = "10-digit Indian mobile number") String mobile,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = Patterns.PIN_CODE, message = "6-digit PIN code") String pinCode,
            @Pattern(regexp = Patterns.GSTIN, message = "15-character GSTIN") String gstin,
            @NotBlank @Pattern(regexp = Patterns.DEVICE_ID) String deviceId,
            @Size(max = 100) String deviceName,
            @Size(max = 20) String appVersion) {}

    /**
     * expectedRole (optional, OWNER or STAFF) is sent by the owner and staff sign-in screens.
     * When the account has the other role, sign-in is refused with ROLE_MISMATCH and no session is created.
     */
    public record LoginRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = Patterns.DEVICE_ID) String deviceId,
            @Size(max = 100) String deviceName,
            @Size(max = 20) String appVersion,
            @Pattern(regexp = "^(OWNER|STAFF)$", message = "OWNER or STAFF") String expectedRole) {}

    public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {}

    public record LogoutRequest(@NotBlank @Size(max = 100) String refreshToken) {}

    public record ReauthRequest(@NotBlank @Size(max = 128) String password) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {}

    public record ForgotPasswordRequest(@NotBlank @Size(max = 40) String username) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {}

    public record MeResponse(UUID userId, String username, String fullName, String role, UUID businessId,
                             String businessName, boolean mustChangePassword, List<String> permissions) {}

    public record SessionResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                                  Instant refreshTokenExpiresAt, MeResponse user) {}

    public record StepUpResponse(String stepUpToken, Instant expiresAt) {}
}
