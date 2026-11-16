package app.stocklist.supplieraccount;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the supplier access token. The user is reloaded on every request, so deactivation and password changes take
 * effect at once. The organisation's review status is not checked here (a supplier can always see its own status);
 * business endpoints check it with {@link SupplierAccess#requireApproved}.
 * Created with "new" inside the supplier security chain only, never as a bean (a bean would run for every request).
 */
public class SupplierJwtAuthFilter extends OncePerRequestFilter {
    private final SupplierJwtService jwt;
    private final SupplierUserRepository users;

    public SupplierJwtAuthFilter(SupplierJwtService jwt, SupplierUserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.verify(header.substring(7).trim()).ifPresent(c -> users.findById(c.userId())
                    .filter(u -> u.isActive() && u.getTokenVersion() == c.tokenVersion()
                            && u.getSupplierOrgId().equals(c.orgId()))
                    .ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    new SupplierPrincipal(u.getId(), u.getSupplierOrgId(), c.deviceId()), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + SupplierPrincipal.ROLE))))));
        }
        chain.doFilter(request, response);
    }
}
