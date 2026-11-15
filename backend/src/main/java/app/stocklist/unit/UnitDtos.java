package app.stocklist.unit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UnitDtos {
    private UnitDtos() {}

    public record UnitInput(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 ._-]{1,20}$", message = "1-20 letters or digits") String code,
            @NotBlank @Size(max = 40) String name) {}

    public record UnitDto(UUID id, String code, String name, boolean builtIn, long version, boolean deleted) {
        public static UnitDto of(UnitOfMeasure u) {
            return new UnitDto(u.getId(), u.getCode(), u.getName(), u.isBuiltIn(), u.getVersion(), u.isDeleted());
        }
    }
}
