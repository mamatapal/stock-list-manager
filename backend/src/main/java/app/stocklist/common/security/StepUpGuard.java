package app.stocklist.common.security;

import app.stocklist.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Sensitive actions (staff, permissions, settings, backups) need a 5-minute step-up token,
 * obtained by re-entering the password at POST /api/v1/auth/reauth, sent as X-Step-Up-Token.
 */
@Component
public class StepUpGuard {
    public static final String HEADER = "X-Step-Up-Token";

    private final JwtService jwtService;

    public StepUpGuard(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public void require() {
        AuthUser user = CurrentUser.get();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        String token = request == null ? null : request.getHeader(HEADER);
        boolean ok = token != null && jwtService.verify(token, TokenClaims.SCOPE_STEP_UP)
                .filter(c -> c.userId().equals(user.userId()) && c.businessId().equals(user.businessId()))
                .isPresent();
        if (!ok) {
            throw ApiException.forbidden("STEP_UP_REQUIRED", "Please re-enter your password to continue");
        }
    }
}
