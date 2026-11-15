package app.stocklist.common.security;

import app.stocklist.common.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw ApiException.unauthorized("UNAUTHENTICATED", "Sign in required");
    }
}
