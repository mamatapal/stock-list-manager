package app.stocklist.supplieraccount;

import app.stocklist.common.error.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The verified Supplier Management user of the current request. The organisation comes only from the token. */
public record SupplierPrincipal(UUID userId, UUID orgId, String deviceId) {
    public static final String ROLE = "SUPPLIER";

    public static SupplierPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SupplierPrincipal p) {
            return p;
        }
        throw ApiException.unauthorized("UNAUTHENTICATED", "Sign in required");
    }
}
