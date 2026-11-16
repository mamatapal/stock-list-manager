package app.stocklist.platformadmin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Portal requests: (1) every state-changing API request must carry the header
 * {@value #CSRF_HEADER}: {@value #CSRF_VALUE}. Browsers do not let another site add custom headers without a CORS
 * preflight, which this server never allows, so a forged cross-site request is refused; the session cookie is also
 * SameSite=Strict. (2) The session cookie, if present, is checked and the administrator put into the security context.
 * Created with "new" inside the admin security chain only.
 */
public class AdminSessionFilter extends OncePerRequestFilter {
    public static final String COOKIE = "SA_SESSION";
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_VALUE = "SupplierAdministration";
    static final String LOGIN_PATH = "/api/v1/admin/auth/login";
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    private final AdminAuthService auth;

    public AdminSessionFilter(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean api = request.getRequestURI().startsWith("/api/v1/admin/");
        AdminPrincipal principal = null;
        String raw = api ? sessionCookie(request) : null;
        if (raw != null) {
            principal = auth.authenticate(raw).orElse(null);
        }
        boolean unsafe = api && !SAFE.contains(request.getMethod());
        boolean signingIn = LOGIN_PATH.equals(request.getRequestURI());
        // Only signed-in requests (and the sign-in itself) can be forged usefully; others get the normal 401.
        if (unsafe && (principal != null || signingIn) && !CSRF_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"code\":\"" + AdminError.CSRF_HEADER_MISSING.name()
                    + "\",\"detail\":\"" + AdminError.CSRF_HEADER_MISSING.message() + "\"}");
            return;
        }
        if (principal != null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,
                    null, List.of(new SimpleGrantedAuthority("ROLE_" + AdminPrincipal.ROLE))));
        }
        chain.doFilter(request, response);
    }

    static String sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
