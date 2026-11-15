package app.stocklist.supplier;

import app.stocklist.common.util.Patterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SupplierDtos {
    private SupplierDtos() {}

    public record SupplierInput(
            @NotBlank(message = "Supplier name is required") @Size(max = 150) String name,
            @Size(max = 120) String contactPerson,
            @Pattern(regexp = "^$|" + Patterns.MOBILE_OR_PHONE, message = "Enter a valid phone number") String mobile,
            @Pattern(regexp = "^$|" + Patterns.MOBILE_OR_PHONE, message = "Enter a valid phone number") String altPhone,
            @Size(max = 300) String address,
            @Size(max = 80) String city,
            @Pattern(regexp = "^$|" + Patterns.GSTIN, message = "15-character GSTIN") String gstin,
            @Size(max = 1000) String notes,
            Boolean active) {}

    public record SupplierDto(UUID id, String name, String contactPerson, String mobile, String altPhone,
                              String address, String city, String gstin, String notes, boolean active, long version,
                              boolean deleted, Instant createdAt, Instant updatedAt, UUID createdBy,
                              UUID updatedBy) {
        public static SupplierDto of(Supplier s) {
            return new SupplierDto(s.getId(), s.getName(), s.getContactPerson(), s.getMobile(), s.getAltPhone(),
                    s.getAddress(), s.getCity(), s.getGstin(), s.getNotes(), s.isActive(), s.getVersion(),
                    s.isDeleted(), s.getCreatedAt(), s.getUpdatedAt(), s.getCreatedBy(), s.getUpdatedBy());
        }
    }
}
