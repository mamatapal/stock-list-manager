package app.stocklist.supplieraccount;

import app.stocklist.common.util.ClientIp;
import app.stocklist.supplieraccount.SupplierDtos.ChangePasswordRequest;
import app.stocklist.supplieraccount.SupplierDtos.EmailRequest;
import app.stocklist.supplieraccount.SupplierDtos.LoginRequest;
import app.stocklist.supplieraccount.SupplierDtos.MeResponse;
import app.stocklist.supplieraccount.SupplierDtos.ProfileRequest;
import app.stocklist.supplieraccount.SupplierDtos.RefreshRequest;
import app.stocklist.supplieraccount.SupplierDtos.RegisterRequest;
import app.stocklist.supplieraccount.SupplierDtos.ResetPasswordRequest;
import app.stocklist.supplieraccount.SupplierDtos.SessionResponse;
import app.stocklist.supplieraccount.SupplierDtos.TokenRequest;
import app.stocklist.supplieraccount.SupplierDtos.VerifyEmailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API of the Supplier Management app. Uses its own supplier tokens (see {@link SupplierJwtService}); shop tokens are
 * not accepted here and supplier tokens are not accepted anywhere else.
 */
@RestController
@RequestMapping("/api/v1/supplier-portal")
@Tag(name = "Supplier portal", description = "Supplier Management app: registration, email links, sign-in, business details")
public class SupplierPortalController {
    private final SupplierAccountService service;

    public SupplierPortalController(SupplierAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Register a supplier business. Always answers 202; a verification link is emailed")
    @SecurityRequirements
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@RequestBody RegisterRequest body, HttpServletRequest req) {
        service.register(body, ClientIp.of(req));
    }

    @Operation(summary = "Confirm the email with the token from the emailed link; the business then waits for approval")
    @SecurityRequirements
    @PostMapping("/auth/verify-email")
    public VerifyEmailResponse verifyEmail(@RequestBody TokenRequest body, HttpServletRequest req) {
        return service.verifyEmail(body.token(), ClientIp.of(req));
    }

    @Operation(summary = "Email a new verification link (at most once a minute). Always answers 202")
    @SecurityRequirements
    @PostMapping("/auth/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@RequestBody EmailRequest body, HttpServletRequest req) {
        service.resendVerification(body.email(), ClientIp.of(req));
    }

    @Operation(summary = "Sign in with email and password. 5 wrong passwords lock the account for 15 minutes")
    @SecurityRequirements
    @PostMapping("/auth/login")
    public SessionResponse login(@RequestBody LoginRequest body, HttpServletRequest req) {
        return service.login(body, ClientIp.of(req));
    }

    @Operation(summary = "Swap a refresh token for a new pair (rotation; a reused token ends the session)")
    @SecurityRequirements
    @PostMapping("/auth/refresh")
    public SessionResponse refresh(@RequestBody RefreshRequest body) {
        return service.refresh(body.refreshToken());
    }

    @Operation(summary = "Sign out this phone")
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest body) {
        service.logout(SupplierPrincipal.current(), body.refreshToken());
    }

    @Operation(summary = "Email a password-reset link. Always answers 202")
    @SecurityRequirements
    @PostMapping("/auth/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@RequestBody EmailRequest body, HttpServletRequest req) {
        service.forgotPassword(body.email(), ClientIp.of(req));
    }

    @Operation(summary = "Set a new password with the token from the emailed link; signs out every phone")
    @SecurityRequirements
    @PostMapping("/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody ResetPasswordRequest body, HttpServletRequest req) {
        service.resetPassword(body.token(), body.newPassword(), ClientIp.of(req));
    }

    @Operation(summary = "Change own password; signs out every other phone and returns a new session")
    @PostMapping("/auth/password/change")
    public SessionResponse changePassword(@RequestBody ChangePasswordRequest body) {
        return service.changePassword(SupplierPrincipal.current(), body);
    }

    @Operation(summary = "The signed-in supplier user and the business, with its review status")
    @GetMapping("/me")
    public MeResponse me() {
        return service.me(SupplierPrincipal.current());
    }

    @Operation(summary = "Change the business details (before approval, or after a rejection: then it is sent for "
            + "review again). Needs If-Match: <org version>")
    @PutMapping("/profile")
    public MeResponse updateProfile(@RequestHeader("If-Match") long version, @RequestBody ProfileRequest body,
                                    HttpServletRequest req) {
        return service.updateProfile(SupplierPrincipal.current(), version, body, ClientIp.of(req));
    }
}
