package app.stocklist.business;

import app.stocklist.common.util.Patterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class BusinessDtos {
    private BusinessDtos() {}

    public record BusinessResponse(UUID id, String name, String address, String city, String state, String pinCode,
                                   String phone, String email, String gstin, boolean hasLogo, long version,
                                   Instant updatedAt) {
        static BusinessResponse of(Business b) {
            return new BusinessResponse(b.getId(), b.getName(), b.getAddress(), b.getCity(), b.getState(),
                    b.getPinCode(), b.getPhone(), b.getEmail(), b.getGstin(), b.getLogoKey() != null,
                    b.getVersion() == null ? 0 : b.getVersion(), b.getUpdatedAt());
        }
    }

    public record UpdateBusinessRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = Patterns.PIN_CODE, message = "6-digit PIN code") String pinCode,
            @NotBlank @Pattern(regexp = Patterns.MOBILE, message = "10-digit Indian mobile number") String phone,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = Patterns.GSTIN, message = "15-character GSTIN") String gstin) {}
}
