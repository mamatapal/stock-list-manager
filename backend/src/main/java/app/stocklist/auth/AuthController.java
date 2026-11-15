package app.stocklist.auth;

import app.stocklist.auth.AuthDtos.ChangePasswordRequest;
import app.stocklist.auth.AuthDtos.ForgotPasswordRequest;
import app.stocklist.auth.AuthDtos.LoginRequest;
import app.stocklist.auth.AuthDtos.LogoutRequest;
import app.stocklist.auth.AuthDtos.MeResponse;
import app.stocklist.auth.AuthDtos.ReauthRequest;
import app.stocklist.auth.AuthDtos.RefreshRequest;
import app.stocklist.auth.AuthDtos.RegisterBusinessRequest;
import app.stocklist.auth.AuthDtos.ResetPasswordRequest;
import app.stocklist.auth.AuthDtos.SessionResponse;
import app.stocklist.auth.AuthDtos.StepUpResponse;
import app.stocklist.common.security.CurrentUser;
import app.stocklist.common.util.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, sign-in, sessions and passwords")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Register a business and its owner account; returns a signed-in session")
    @SecurityRequirements
    @PostMapping("/register-business")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse register(@Valid @RequestBody RegisterBusinessRequest body, HttpServletRequest req) {
        return auth.registerBusiness(body, ClientIp.of(req));
    }

    @Operation(summary = "Sign in (owner or staff). 5 wrong passwords lock the account for 15 minutes")
    @SecurityRequirements
    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest req) {
        return auth.login(body, ClientIp.of(req));
    }

    @Operation(summary = "Swap a refresh token for a new access + refresh token pair (rotation)")
    @SecurityRequirements
    @PostMapping("/refresh")
    public SessionResponse refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest req) {
        return auth.refresh(body.refreshToken(), ClientIp.of(req));
    }

    @Operation(summary = "Sign out this phone: revokes its refresh-token family")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest body, HttpServletRequest req) {
        auth.logout(CurrentUser.get(), body.refreshToken(), ClientIp.of(req));
    }

    @Operation(summary = "Re-enter the password to get a 5-minute step-up token for sensitive actions")
    @PostMapping("/reauth")
    public StepUpResponse reauth(@Valid @RequestBody ReauthRequest body, HttpServletRequest req) {
        return auth.reauthenticate(CurrentUser.get(), body.password(), ClientIp.of(req));
    }

    @Operation(summary = "Change own password; signs out every other phone")
    @PostMapping("/password/change")
    public SessionResponse changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest req) {
        return auth.changePassword(CurrentUser.get(), body, ClientIp.of(req));
    }

    @Operation(summary = "Owner: email a single-use reset code. Always answers 202")
    @SecurityRequirements
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest req) {
        auth.forgotPassword(body.username(), ClientIp.of(req));
    }

    @Operation(summary = "Set a new password with the emailed reset code")
    @SecurityRequirements
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest req) {
        auth.resetPassword(body.token(), body.newPassword(), ClientIp.of(req));
    }

    @Operation(summary = "The signed-in user, business and effective permissions")
    @GetMapping("/me")
    public MeResponse me() {
        return auth.me(CurrentUser.get());
    }
}
