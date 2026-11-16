package app.stocklist.platformadmin;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in administrator of the current portal request. */
public record AdminPrincipal(UUID adminId, String username, String fullName, UUID sessionId) {
    public static final String ROLE = "PLATFORM_ADMIN";

    public static AdminPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) {
            return p;
        }
        throw AdminError.UNAUTHENTICATED.ex();
    }
}
