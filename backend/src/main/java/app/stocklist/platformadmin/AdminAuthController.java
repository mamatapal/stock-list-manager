package app.stocklist.platformadmin;

import app.stocklist.common.util.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in of the Supplier Administration portal. The session lives in an HttpOnly, SameSite=Strict cookie. */
@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Supplier Administration", description = "Administrator portal (session cookie + X-Requested-With header)")
public class AdminAuthController {
    private final AdminAuthService auth;
    private final AdminProperties props;

    public AdminAuthController(AdminAuthService auth, AdminProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @Operation(summary = "Sign in with username, password and the 6-digit authenticator-app code")
    @SecurityRequirements
    @PostMapping("/login")
    public AdminDtos.AdminMe login(@RequestBody AdminDtos.LoginRequest body, HttpServletRequest req,
                                   HttpServletResponse res) {
        AdminAuthService.SignedIn s = auth.login(body, ClientIp.of(req));
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(s.rawSessionId(), props.sessionMax()).toString());
        return s.me();
    }

    @Operation(summary = "Sign out: ends this portal session")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse res) {
        auth.logout(AdminPrincipal.current());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    @Operation(summary = "The signed-in administrator")
    @GetMapping("/me")
    public AdminDtos.AdminMe me() {
        return auth.me(AdminPrincipal.current());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(AdminSessionFilter.COOKIE, value)
                .httpOnly(true)
                .secure(props.secureCookie())
                .sameSite("Strict")
                .path("/api/v1/admin")
                .maxAge(maxAge)
                .build();
    }
}
