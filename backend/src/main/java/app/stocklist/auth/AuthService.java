package app.stocklist.auth;

import app.stocklist.auth.AuthDtos.ChangePasswordRequest;
import app.stocklist.auth.AuthDtos.LoginRequest;
import app.stocklist.auth.AuthDtos.MeResponse;
import app.stocklist.auth.AuthDtos.RegisterBusinessRequest;
import app.stocklist.auth.AuthDtos.SessionResponse;
import app.stocklist.auth.AuthDtos.StepUpResponse;
import app.stocklist.business.Business;
import app.stocklist.business.BusinessDetails;
import app.stocklist.business.BusinessRepository;
import app.stocklist.business.BusinessSeq;
import app.stocklist.business.BusinessSeqRepository;
import app.stocklist.common.audit.AuditAction;
import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.security.JwtService;
import app.stocklist.common.security.LoginRateLimiter;
import app.stocklist.common.security.PasswordPolicy;
import app.stocklist.common.util.Hashing;
import app.stocklist.config.AppProperties;
import app.stocklist.user.AppUser;
import app.stocklist.user.AppUserRepository;
import app.stocklist.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registration, login, refresh, logout, step-up, password change and reset.
 * Failure paths that must be remembered (failed-login counters, token-reuse revocation) are committed
 * before the error is returned, which is why these use TransactionTemplate instead of @Transactional.
 */
@Service
public class AuthService {
    static final String INVALID_LOGIN = "Invalid username or password. After 5 failed attempts the account "
            + "is locked for 15 minutes.";

    private final AppUserRepository users;
    private final BusinessRepository businesses;
    private final BusinessSeqRepository businessSeqs;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionService sessions;
    private final JwtService jwtService;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final PasswordResetMailer mailer;
    private final AppProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AppUserRepository users, BusinessRepository businesses, BusinessSeqRepository businessSeqs,
                       RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                       SessionService sessions, JwtService jwtService, PasswordEncoder encoder,
                       LoginRateLimiter rateLimiter, AuditService audit, PasswordResetMailer mailer,
                       AppProperties props, TransactionTemplate tx, Clock clock) {
        this.users = users;
        this.businesses = businesses;
        this.businessSeqs = businessSeqs;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.jwtService = jwtService;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.mailer = mailer;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
        // Used so that an unknown username takes as long to reject as a wrong password.
        this.dummyHash = encoder.encode("timing-equaliser-" + Hashing.newOpaqueToken());
    }

    public static String normaliseUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- registration

    public SessionResponse registerBusiness(RegisterBusinessRequest r, String ip) {
        String username = normaliseUsername(r.username());
        PasswordPolicy.validate(r.password(), username);
        String hash = encoder.encode(r.password());
        return tx.execute(status -> {
            if (users.existsByUsername(username)) {
                throw ApiException.conflict("USERNAME_TAKEN", "This username is already in use");
            }
            Instant now = Instant.now(clock);
            Business business = businesses.save(new Business(new BusinessDetails(
                    r.businessName().trim(), r.address().trim(), r.city().trim(), r.state().trim(), r.pinCode(),
                    r.mobile(), r.email().trim(), blankToNull(r.gstin())), now));
            businessSeqs.save(BusinessSeq.create(business.getId()));
            AppUser owner = users.save(new AppUser(business.getId(), username, r.ownerName().trim(), r.mobile(),
                    r.email().trim(), hash, Role.OWNER, false, null, now));
            owner.recordSuccessfulLogin(now);
            audit.record(business.getId(), owner.getId(), AuditAction.BUSINESS_REGISTERED, "BUSINESS",
                    business.getId(), Map.of("name", business.getName()), r.deviceId(), ip);
            audit.record(business.getId(), owner.getId(), AuditAction.LOGIN, "USER", owner.getId(), null,
                    r.deviceId(), ip);
            return sessions.startSession(owner, r.deviceId(), r.deviceName(), r.appVersion());
        });
    }

    // ---------------------------------------------------------------- login

    public SessionResponse login(LoginRequest r, String ip) {
        if (!rateLimiter.tryAcquire(ip)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many sign-in attempts. Please wait a minute and try again.");
        }
        String username = normaliseUsername(r.username());
        Outcome<SessionResponse> outcome = tx.execute(status -> attemptLogin(username, r, ip));
        return outcome.get();
    }

    private Outcome<SessionResponse> attemptLogin(String username, LoginRequest r, String ip) {
        Instant now = Instant.now(clock);
        Optional<AppUser> found = users.findByUsername(username);
        if (found.isEmpty()) {
            encoder.matches(r.password(), dummyHash);
            audit.record(null, null, AuditAction.LOGIN_FAILED, "USER", null,
                    Map.of("username", username, "reason", "UNKNOWN_USER"), r.deviceId(), ip);
            return Outcome.fail(ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_LOGIN));
        }
        AppUser user = found.get();
        if (user.isLocked(now)) {
            audit.record(user.getBusinessId(), user.getId(), AuditAction.LOGIN_FAILED, "USER", user.getId(),
                    Map.of("reason", "LOCKED"), r.deviceId(), ip);
            return Outcome.fail(ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_LOGIN));
        }
        if (!encoder.matches(r.password(), user.getPasswordHash())) {
            registerFailure(user, now, r.deviceId(), ip, "WRONG_PASSWORD");
            return Outcome.fail(ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_LOGIN));
        }
        // Correct password: only now is it safe to say why access is refused.
        if (!user.isActive()) {
            audit.record(user.getBusinessId(), user.getId(), AuditAction.LOGIN_FAILED, "USER", user.getId(),
                    Map.of("reason", "INACTIVE"), r.deviceId(), ip);
            return Outcome.fail(ApiException.forbidden("ACCOUNT_INACTIVE",
                    "This account has been deactivated. Please contact the business owner."));
        }
        if (!businesses.findById(user.getBusinessId()).map(Business::isActive).orElse(false)) {
            return Outcome.fail(ApiException.forbidden("BUSINESS_INACTIVE", "This business account is not active."));
        }
        if (r.expectedRole() != null && !r.expectedRole().equals(user.getRole().name())) {
            audit.record(user.getBusinessId(), user.getId(), AuditAction.LOGIN_FAILED, "USER", user.getId(),
                    Map.of("reason", "ROLE_MISMATCH"), r.deviceId(), ip);
            return Outcome.fail(ApiException.forbidden("ROLE_MISMATCH", user.isOwner()
                    ? "This is the owner's account. Please use Owner sign-in."
                    : "This is a staff account. Please use Staff sign-in."));
        }
        user.recordSuccessfulLogin(now);
        audit.record(user.getBusinessId(), user.getId(), AuditAction.LOGIN, "USER", user.getId(), null,
                r.deviceId(), ip);
        return Outcome.ok(sessions.startSession(user, r.deviceId(), r.deviceName(), r.appVersion()));
    }

    private void registerFailure(AppUser user, Instant now, String deviceId, String ip, String reason) {
        boolean locked = user.recordFailedLogin(props.lockout().maxFailures(), now.plus(props.lockout().duration()));
        audit.record(user.getBusinessId(), user.getId(), AuditAction.LOGIN_FAILED, "USER", user.getId(),
                Map.of("reason", reason), deviceId, ip);
        if (locked) {
            audit.record(user.getBusinessId(), user.getId(), AuditAction.ACCOUNT_LOCKED, "USER", user.getId(),
                    Map.of("minutes", props.lockout().duration().toMinutes()), deviceId, ip);
        }
    }

    // ---------------------------------------------------------------- refresh / logout

    public SessionResponse refresh(String rawRefreshToken, String ip) {
        Outcome<SessionResponse> outcome = tx.execute(status -> attemptRefresh(rawRefreshToken, ip));
        return outcome.get();
    }

    private Outcome<SessionResponse> attemptRefresh(String raw, String ip) {
        Instant now = Instant.now(clock);
        ApiException invalid = ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Please sign in again");
        Optional<RefreshToken> found = refreshTokens.findForUpdate(Hashing.sha256Hex(raw));
        if (found.isEmpty()) {
            return Outcome.fail(invalid);
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            if (RefreshToken.REASON_ROTATED.equals(token.getRevokeReason())) {
                // An already-used token came back: assume it was stolen and end the whole family.
                refreshTokens.revokeFamily(token.getFamilyId(), RefreshToken.REASON_REUSE, now);
                audit.record(token.getBusinessId(), token.getUserId(), AuditAction.TOKEN_REUSE_DETECTED,
                        "SESSION", token.getFamilyId(), null, token.getDeviceId(), ip);
            }
            return Outcome.fail(invalid);
        }
        if (token.isExpired(now)) {
            return Outcome.fail(invalid);
        }
        AppUser user = users.findByBusinessIdAndId(token.getBusinessId(), token.getUserId()).orElse(null);
        boolean businessActive = user != null
                && businesses.findById(user.getBusinessId()).map(Business::isActive).orElse(false);
        if (user == null || !user.isActive() || !businessActive) {
            refreshTokens.revokeFamily(token.getFamilyId(), RefreshToken.REASON_INACTIVE, now);
            return Outcome.fail(ApiException.forbidden("ACCOUNT_INACTIVE",
                    "This account has been deactivated. Please contact the business owner."));
        }
        return Outcome.ok(sessions.rotate(user, token).session());
    }

    public void logout(AuthUser caller, String rawRefreshToken, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            refreshTokens.findForUpdate(Hashing.sha256Hex(rawRefreshToken))
                    .filter(t -> t.getUserId().equals(caller.userId()) && t.getBusinessId().equals(caller.businessId()))
                    .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), RefreshToken.REASON_LOGOUT, now));
            audit.record(caller.businessId(), caller.userId(), AuditAction.LOGOUT, "USER", caller.userId(), null,
                    caller.deviceId(), ip);
        });
    }

    // ---------------------------------------------------------------- step-up, password change

    public StepUpResponse reauthenticate(AuthUser caller, String password, String ip) {
        if (!rateLimiter.tryAcquire(ip)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many attempts. Please wait a minute and try again.");
        }
        Outcome<StepUpResponse> outcome = tx.execute(status -> {
            Instant now = Instant.now(clock);
            AppUser user = loadCaller(caller);
            if (user.isLocked(now) || !encoder.matches(password, user.getPasswordHash())) {
                if (!user.isLocked(now)) {
                    registerFailure(user, now, caller.deviceId(), ip, "WRONG_PASSWORD_REAUTH");
                }
                return Outcome.fail(ApiException.unauthorized("INVALID_CREDENTIALS", "Incorrect password"));
            }
            user.recordSuccessfulLogin(now);
            audit.record(user.getBusinessId(), user.getId(), AuditAction.REAUTHENTICATED, "USER", user.getId(),
                    null, caller.deviceId(), ip);
            JwtService.IssuedToken t = jwtService.issueStepUp(user, caller.deviceId());
            return Outcome.ok(new StepUpResponse(t.token(), t.expiresAt()));
        });
        return outcome.get();
    }

    public SessionResponse changePassword(AuthUser caller, ChangePasswordRequest r, String ip) {
        Outcome<SessionResponse> outcome = tx.execute(status -> {
            Instant now = Instant.now(clock);
            AppUser user = loadCaller(caller);
            if (!encoder.matches(r.currentPassword(), user.getPasswordHash())) {
                registerFailure(user, now, caller.deviceId(), ip, "WRONG_PASSWORD_CHANGE");
                return Outcome.fail(ApiException.unauthorized("INVALID_CREDENTIALS", "Current password is incorrect"));
            }
            PasswordPolicy.validate(r.newPassword(), user.getUsername());
            if (encoder.matches(r.newPassword(), user.getPasswordHash())) {
                return Outcome.fail(ApiException.badRequest("WEAK_PASSWORD",
                        "The new password must be different from the current one"));
            }
            user.changePassword(encoder.encode(r.newPassword()), false, now);
            refreshTokens.revokeAllForUser(user.getBusinessId(), user.getId(), RefreshToken.REASON_PASSWORD, now);
            audit.record(user.getBusinessId(), user.getId(), AuditAction.PASSWORD_CHANGED, "USER", user.getId(),
                    null, caller.deviceId(), ip);
            // Every other device is signed out; this one gets a fresh session.
            return Outcome.ok(sessions.startSession(user, caller.deviceId(), null, null));
        });
        return outcome.get();
    }

    public MeResponse me(AuthUser caller) {
        return tx.execute(status -> sessions.me(loadCaller(caller)));
    }

    // ---------------------------------------------------------------- password reset (owner, by email)

    /** Always succeeds from the caller's point of view, so it cannot be used to discover usernames. */
    public void forgotPassword(String rawUsername, String ip) {
        if (!rateLimiter.tryAcquire(ip)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many attempts. Please wait a minute and try again.");
        }
        String username = normaliseUsername(rawUsername);
        tx.executeWithoutResult(status -> users.findByUsername(username)
                .filter(u -> u.isOwner() && u.isActive() && u.getEmail() != null)
                .ifPresent(u -> {
                    Instant now = Instant.now(clock);
                    String raw = Hashing.newOpaqueToken();
                    resetTokens.save(new PasswordResetToken(u.getBusinessId(), u.getId(), Hashing.sha256Hex(raw), now,
                            now.plus(props.resetTokenTtl())));
                    audit.record(u.getBusinessId(), u.getId(), AuditAction.PASSWORD_RESET_REQUESTED, "USER",
                            u.getId(), null, null, ip);
                    mailer.send(u, raw);
                }));
    }

    public void resetPassword(String rawToken, String newPassword, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            PasswordResetToken token = resetTokens.findForUpdate(Hashing.sha256Hex(rawToken))
                    .filter(t -> t.isUsable(now))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN",
                            "This reset link is invalid or has expired"));
            AppUser user = users.findByBusinessIdAndId(token.getBusinessId(), token.getUserId())
                    .orElseThrow(() -> ApiException.notFound("User"));
            PasswordPolicy.validate(newPassword, user.getUsername());
            user.changePassword(encoder.encode(newPassword), false, now);
            token.markUsed(now);
            refreshTokens.revokeAllForUser(user.getBusinessId(), user.getId(), RefreshToken.REASON_PASSWORD, now);
            audit.record(user.getBusinessId(), user.getId(), AuditAction.PASSWORD_RESET, "USER", user.getId(),
                    null, null, ip);
        });
    }

    private AppUser loadCaller(AuthUser caller) {
        return users.findByBusinessIdAndId(caller.businessId(), caller.userId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Sign in required"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Lets a transaction commit its side effects and still report an error afterwards. */
    record Outcome<T>(T value, ApiException error) {
        static <T> Outcome<T> ok(T value) { return new Outcome<>(value, null); }
        static <T> Outcome<T> fail(ApiException error) { return new Outcome<>(null, error); }

        T get() {
            if (error != null) throw error;
            return value;
        }
    }
}
