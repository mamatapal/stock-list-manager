package app.stocklist.common.security;

import app.stocklist.business.BusinessRepository;
import app.stocklist.business.Business;
import app.stocklist.permission.Permission;
import app.stocklist.permission.UserPermission;
import app.stocklist.permission.UserPermissionRepository;
import app.stocklist.user.AppUser;
import app.stocklist.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks, on every request: signature and expiry, user still ACTIVE, token version unchanged,
 * business active and matching the token. Permissions are re-read from the database each time,
 * so a revoked permission or a deactivation takes effect on the very next call.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AppUserRepository users;
    private final BusinessRepository businesses;
    private final UserPermissionRepository permissions;

    public JwtAuthFilter(JwtService jwtService, AppUserRepository users, BusinessRepository businesses,
                         UserPermissionRepository permissions) {
        this.jwtService = jwtService;
        this.users = users;
        this.businesses = businesses;
        this.permissions = permissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Authenticated> result = authenticate(header.substring(7).trim());
            if (result.isPresent()) {
                Authenticated a = result.get();
                if (a.mustChangePassword() && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(request.getRequestURI())) {
                    // A temporary password only opens the "choose your own password" step.
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write("{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                            + "\"detail\":\"Please choose your own password first\"}");
                    return;
                }
                AuthUser user = a.user();
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/password/change", "/api/v1/auth/logout");

    private record Authenticated(AuthUser user, boolean mustChangePassword) {}

    private Optional<Authenticated> authenticate(String token) {
        Optional<TokenClaims> claims = jwtService.verify(token, TokenClaims.SCOPE_ACCESS);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        TokenClaims c = claims.get();
        Optional<AppUser> found = users.findById(c.userId());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AppUser u = found.get();
        if (!u.isActive() || u.getTokenVersion() != c.tokenVersion() || !u.getBusinessId().equals(c.businessId())) {
            return Optional.empty();
        }
        if (!businesses.findById(u.getBusinessId()).map(Business::isActive).orElse(false)) {
            return Optional.empty();
        }
        Set<Permission> perms = EnumSet.noneOf(Permission.class);
        if (!u.isOwner()) {
            for (UserPermission p : permissions.findByBusinessIdAndUserId(u.getBusinessId(), u.getId())) {
                perms.add(p.permission());
            }
        }
        return Optional.of(new Authenticated(
                new AuthUser(u.getId(), u.getBusinessId(), u.getRole(), u.getUsername(), c.deviceId(), perms),
                u.isMustChangePassword()));
    }
}
