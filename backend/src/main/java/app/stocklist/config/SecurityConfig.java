package app.stocklist.config;

import app.stocklist.business.BusinessRepository;
import app.stocklist.common.security.JwtAuthFilter;
import app.stocklist.common.security.JwtService;
import app.stocklist.permission.UserPermissionRepository;
import app.stocklist.user.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register-business",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, AppUserRepository users,
                                                   BusinessRepository businesses,
                                                   UserPermissionRepository permissions) throws Exception {
        http
                .csrf(csrf -> csrf.disable())          // stateless bearer-token API, no cookies
                .cors(cors -> cors.disable())          // mobile clients only
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res, 401, "UNAUTHENTICATED",
                                "Sign in required"))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, 403, "PERMISSION_DENIED",
                                "You do not have permission for this action")))
                .addFilterBefore(new JwtAuthFilter(jwtService, users, businesses, permissions),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse res, int status, String code,
                                     String detail) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }

    /** Argon2id: 16-byte salt, 32-byte hash, 1 lane, 19 MiB memory, 2 iterations. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    /** No form/basic login users exist; this stops Spring Boot creating a default in-memory user. */
    @Bean
    public UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Not supported");
        };
    }
}
