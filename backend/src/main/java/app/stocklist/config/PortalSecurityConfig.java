package app.stocklist.config;

import app.stocklist.platformadmin.AdminAuthService;
import app.stocklist.platformadmin.AdminError;
import app.stocklist.platformadmin.AdminPrincipal;
import app.stocklist.platformadmin.AdminSessionFilter;
import app.stocklist.supplieraccount.SupplierJwtAuthFilter;
import app.stocklist.supplieraccount.SupplierJwtService;
import app.stocklist.supplieraccount.SupplierPrincipal;
import app.stocklist.supplieraccount.SupplierUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security for the supplier side, kept apart from the shop app's chain in {@link SecurityConfig} (which stays the
 * last, catch-all chain and is otherwise unchanged). Each chain below only handles its own paths:
 * <ol>
 *   <li>{@code /api/v1/supplier-portal/**}: Supplier Management app, supplier bearer tokens only;</li>
 *   <li>{@code /api/v1/admin/**} and {@code /admin/**}: Supplier Administration portal, session cookie;</li>
 *   <li>{@code /supplier/**}: the public pages opened from emailed links.</li>
 * </ol>
 * A shop token is never accepted by chains 1 and 2, and a supplier token is never accepted by the shop chain.
 */
@Configuration
public class PortalSecurityConfig {

    private static final String[] SUPPLIER_PUBLIC_POST = {
            "/api/v1/supplier-portal/auth/register",
            "/api/v1/supplier-portal/auth/verify-email",
            "/api/v1/supplier-portal/auth/resend-verification",
            "/api/v1/supplier-portal/auth/login",
            "/api/v1/supplier-portal/auth/refresh",
            "/api/v1/supplier-portal/auth/password/forgot",
            "/api/v1/supplier-portal/auth/password/reset"
    };

    /** Pages: only our own scripts and styles, no framing, no referrer (emailed tokens sit in the URL fragment). */
    private static final String PAGE_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; "
            + "base-uri 'none'; object-src 'none'";

    @Bean
    @Order(1)
    public SecurityFilterChain supplierPortalChain(HttpSecurity http, SupplierJwtService jwt,
                                                   SupplierUserRepository users) throws Exception {
        http
                .securityMatcher("/api/v1/supplier-portal/**")
                .csrf(c -> c.disable())          // bearer tokens, no cookies
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, SUPPLIER_PUBLIC_POST).permitAll()
                        .anyRequest().hasRole(SupplierPrincipal.ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problem(res, 401, "UNAUTHENTICATED",
                                "Sign in required"))
                        .accessDeniedHandler((req, res, ex) -> problem(res, 403, "PERMISSION_DENIED",
                                "You do not have permission for this action")))
                .addFilterBefore(new SupplierJwtAuthFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain supplierAdministrationChain(HttpSecurity http, AdminAuthService admins)
            throws Exception {
        http
                .securityMatcher("/api/v1/admin/**", "/admin", "/admin/**")
                // Cookie session: forged cross-site requests are stopped by SameSite=Strict and by the
                // X-Requested-With header check in AdminSessionFilter.
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(PAGE_CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/admin", "/admin/", "/admin/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                        .anyRequest().hasRole(AdminPrincipal.ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problem(res, 401,
                                AdminError.UNAUTHENTICATED.name(), AdminError.UNAUTHENTICATED.message()))
                        .accessDeniedHandler((req, res, ex) -> problem(res, 403, "PERMISSION_DENIED",
                                "You do not have permission for this action")))
                .addFilterBefore(new AdminSessionFilter(admins), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain supplierLinkPagesChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/supplier/**")
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(PAGE_CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/supplier/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    private static void problem(HttpServletResponse res, int status, String code, String detail) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }
}
