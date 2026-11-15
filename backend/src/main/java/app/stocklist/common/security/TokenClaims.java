package app.stocklist.common.security;

import java.time.Instant;
import java.util.UUID;

public record TokenClaims(UUID userId, UUID businessId, String role, int tokenVersion, String deviceId,
                          String scope, Instant expiresAt) {
    public static final String SCOPE_ACCESS = "access";
    public static final String SCOPE_STEP_UP = "step_up";
}
