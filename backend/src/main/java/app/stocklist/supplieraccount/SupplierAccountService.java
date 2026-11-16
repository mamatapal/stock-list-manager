package app.stocklist.supplieraccount;

import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.LoginRateLimiter;
import app.stocklist.common.util.Hashing;
import app.stocklist.config.AppProperties;
import app.stocklist.supplieraccount.SupplierDtos.ChangePasswordRequest;
import app.stocklist.supplieraccount.SupplierDtos.LoginRequest;
import app.stocklist.supplieraccount.SupplierDtos.MeResponse;
import app.stocklist.supplieraccount.SupplierDtos.ProfileRequest;
import app.stocklist.supplieraccount.SupplierDtos.RegisterRequest;
import app.stocklist.supplieraccount.SupplierDtos.SessionResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accounts of the Supplier Management app: registration, email verification by link, sign-in, sessions, password
 * reset by link, and the business details. No SMS and no one-time codes are used anywhere.
 * <p>
 * Answers never reveal whether an email address is registered: registration, "resend link" and "forgot password"
 * answer the same in every case, and sign-in failures all give the same error.
 * Emails are sent after the database transaction has committed.
 */
@Service
public class SupplierAccountService {
    static final String VERIFY_PATH = "/supplier/verify-email.html#t=";
    static final String RESET_PATH = "/supplier/reset-password.html#t=";

    private final SupplierOrgRepository orgs;
    private final SupplierUserRepository users;
    private final SupplierRefreshTokenRepository refreshTokens;
    private final SupplierEmailTokenRepository emailTokens;
    private final SupplierOrgEventRepository events;
    private final SupplierJwtService jwt;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter rateLimiter;
    private final SupplierMailer mailer;
    private final SupplierProperties props;
    private final AppProperties app;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String dummyHash;

    public SupplierAccountService(SupplierOrgRepository orgs, SupplierUserRepository users,
                                  SupplierRefreshTokenRepository refreshTokens,
                                  SupplierEmailTokenRepository emailTokens, SupplierOrgEventRepository events,
                                  SupplierJwtService jwt, PasswordEncoder encoder, LoginRateLimiter rateLimiter,
                                  SupplierMailer mailer, SupplierProperties props, AppProperties app,
                                  TransactionTemplate tx, Clock clock) {
        this.orgs = orgs;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.emailTokens = emailTokens;
        this.events = events;
        this.jwt = jwt;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.mailer = mailer;
        this.props = props;
        this.app = app;
        this.tx = tx;
        this.clock = clock;
        // An unknown email takes as long to reject as a wrong password.
        this.dummyHash = encoder.encode("timing-equaliser-" + Hashing.newOpaqueToken());
    }

    /** An email to send once the transaction has committed. */
    private interface Mail extends Runnable {}

    // ---------------------------------------------------------------- registration and email verification

    /** Always answers the same (202), whether or not the email already has an account. */
    public void register(RegisterRequest r, String ip) {
        limit(ip);
        String email = SupplierInputs.email(r.email());
        SupplierDtos.BusinessDetails details = SupplierInputs.details(r.legalName(), r.tradeName(),
                r.contactPerson(), r.mobile(), r.address(), r.city(), r.state(), r.pinCode(), r.gstin());
        SupplierInputs.password(r.password(), email);
        String hash = encoder.encode(r.password());
        List<Mail> mails = tx.execute(status -> {
            Instant now = Instant.now(clock);
            List<Mail> out = new ArrayList<>();
            Optional<SupplierUser> existing = users.findByEmail(email);
            if (existing.isPresent()) {
                SupplierUser u = existing.get();
                newLink(u, SupplierEmailToken.PASSWORD_RESET, now)
                        .ifPresent(link -> out.add(() -> mailer.alreadyRegistered(u, link)));
                return out;
            }
            SupplierOrg org = orgs.save(new SupplierOrg(details, email, now));
            SupplierUser user = users.save(new SupplierUser(org.getId(), email, details.contactPerson(),
                    details.mobile(), hash, now));
            event(org, SupplierOrgEvent.REGISTERED, user.getId(), null, org.getStatus(), null, ip, now);
            newLink(user, SupplierEmailToken.VERIFY_EMAIL, now)
                    .ifPresent(link -> out.add(() -> mailer.verificationLink(user, link)));
            return out;
        });
        mails.forEach(Runnable::run);
    }

    /** Marks the email verified and sends the business for review. A repeated click on a used link is harmless. */
    public SupplierDtos.VerifyEmailResponse verifyEmail(String rawToken, String ip) {
        return tx.execute(status -> {
            Instant now = Instant.now(clock);
            SupplierEmailToken t = emailToken(rawToken, SupplierEmailToken.VERIFY_EMAIL);
            SupplierUser user = users.findById(t.getUserId()).orElseThrow(SupplierPortalError.LINK_INVALID::ex);
            SupplierOrg org = orgs.findForUpdate(user.getSupplierOrgId())
                    .orElseThrow(SupplierPortalError.LINK_INVALID::ex);
            if (user.isEmailVerified()) {
                t.markUsed(now);
                return new SupplierDtos.VerifyEmailResponse(org.getStatus());
            }
            checkUsable(t, now);
            t.markUsed(now);
            markVerified(user, org, ip, now);
            return new SupplierDtos.VerifyEmailResponse(org.getStatus());
        });
    }

    /** Always answers 202. Sends a new link only to an unverified account, at most once a minute. */
    public void resendVerification(String rawEmail, String ip) {
        limit(ip);
        String email = SupplierInputs.emailKey(rawEmail);
        List<Mail> mails = tx.execute(status -> {
            List<Mail> out = new ArrayList<>();
            users.findByEmail(email).filter(u -> u.isActive() && !u.isEmailVerified()).ifPresent(u ->
                    newLink(u, SupplierEmailToken.VERIFY_EMAIL, Instant.now(clock))
                            .ifPresent(link -> out.add(() -> mailer.verificationLink(u, link))));
            return out;
        });
        mails.forEach(Runnable::run);
    }

    // ---------------------------------------------------------------- sign in, sessions

    public SessionResponse login(LoginRequest r, String ip) {
        limit(ip);
        String deviceId = deviceId(r.deviceId());
        String email = SupplierInputs.emailKey(r.email());
        String password = r.password() == null ? "" : r.password();
        Outcome<SessionResponse> outcome = tx.execute(status -> {
            Instant now = Instant.now(clock);
            Optional<SupplierUser> found = users.findByEmail(email);
            if (found.isEmpty()) {
                encoder.matches(password, dummyHash);
                return Outcome.fail(SupplierPortalError.INVALID_CREDENTIALS.ex());
            }
            SupplierUser user = found.get();
            if (user.isLocked(now)) {
                return Outcome.fail(SupplierPortalError.INVALID_CREDENTIALS.ex());
            }
            if (!encoder.matches(password, user.getPasswordHash())) {
                user.recordFailedLogin(app.lockout().maxFailures(), now.plus(app.lockout().duration()));
                return Outcome.fail(SupplierPortalError.INVALID_CREDENTIALS.ex());
            }
            // Correct password: only now is it safe to say why access is refused.
            if (!user.isActive()) {
                return Outcome.fail(SupplierPortalError.ACCOUNT_INACTIVE.ex());
            }
            user.recordSuccessfulLogin(now);
            return Outcome.ok(startSession(user, deviceId, trimTo(r.deviceName(), 100), now));
        });
        return outcome.get();
    }

    public SessionResponse refresh(String raw) {
        Outcome<SessionResponse> outcome = tx.execute(status -> {
            Instant now = Instant.now(clock);
            ApiException invalid = SupplierPortalError.INVALID_REFRESH_TOKEN.ex();
            Optional<SupplierRefreshToken> found = raw == null ? Optional.empty()
                    : refreshTokens.findForUpdate(Hashing.sha256Hex(raw));
            if (found.isEmpty()) return Outcome.fail(invalid);
            SupplierRefreshToken token = found.get();
            if (token.isRevoked()) {
                if (SupplierRefreshToken.REASON_ROTATED.equals(token.getRevokeReason())) {
                    // A used token came back: assume it was stolen and end the whole family.
                    refreshTokens.revokeFamily(token.getFamilyId(), SupplierRefreshToken.REASON_REUSE, now);
                }
                return Outcome.fail(invalid);
            }
            if (token.isExpired(now)) return Outcome.fail(invalid);
            SupplierUser user = users.findById(token.getUserId()).orElse(null);
            if (user == null || !user.isActive()) {
                refreshTokens.revokeFamily(token.getFamilyId(), SupplierRefreshToken.REASON_INACTIVE, now);
                return Outcome.fail(SupplierPortalError.ACCOUNT_INACTIVE.ex());
            }
            String next = Hashing.newOpaqueToken();
            SupplierRefreshToken rotated = refreshTokens.save(new SupplierRefreshToken(user.getId(),
                    token.getFamilyId(), Hashing.sha256Hex(next), token.getDeviceId(), token.getDeviceName(), now,
                    now.plus(props.refreshTtl())));
            token.rotatedTo(rotated.getId(), now);
            SupplierJwtService.Issued access = jwt.issue(user, token.getDeviceId());
            return Outcome.ok(new SessionResponse(access.token(), access.expiresAt(), next, rotated.getExpiresAt(),
                    me(user)));
        });
        return outcome.get();
    }

    public void logout(SupplierPrincipal caller, String raw) {
        tx.executeWithoutResult(status -> {
            if (raw == null) return;
            refreshTokens.findForUpdate(Hashing.sha256Hex(raw))
                    .filter(t -> t.getUserId().equals(caller.userId()))
                    .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), SupplierRefreshToken.REASON_LOGOUT,
                            Instant.now(clock)));
        });
    }

    // ---------------------------------------------------------------- passwords

    /** Always answers 202. The link proves the person can read the account's email. */
    public void forgotPassword(String rawEmail, String ip) {
        limit(ip);
        String email = SupplierInputs.emailKey(rawEmail);
        List<Mail> mails = tx.execute(status -> {
            List<Mail> out = new ArrayList<>();
            users.findByEmail(email).filter(SupplierUser::isActive).ifPresent(u ->
                    newLink(u, SupplierEmailToken.PASSWORD_RESET, Instant.now(clock))
                            .ifPresent(link -> out.add(() -> mailer.passwordResetLink(u, link))));
            return out;
        });
        mails.forEach(Runnable::run);
    }

    /** Sets the new password and signs out every phone. Opening the emailed link also proves the email address. */
    public void resetPassword(String rawToken, String newPassword, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            SupplierEmailToken t = emailToken(rawToken, SupplierEmailToken.PASSWORD_RESET);
            checkUsable(t, now);
            SupplierUser user = users.findById(t.getUserId()).orElseThrow(SupplierPortalError.LINK_INVALID::ex);
            if (!user.isActive()) throw SupplierPortalError.ACCOUNT_INACTIVE.ex();
            SupplierInputs.password(newPassword, user.getEmail());
            user.changePassword(encoder.encode(newPassword), now);
            t.markUsed(now);
            refreshTokens.revokeAllForUser(user.getId(), SupplierRefreshToken.REASON_PASSWORD, now);
            if (!user.isEmailVerified()) {
                SupplierOrg org = orgs.findForUpdate(user.getSupplierOrgId()).orElseThrow();
                markVerified(user, org, ip, now);
            }
        });
    }

    /** Changes the password, signs out every other phone and returns a new session for this one. */
    public SessionResponse changePassword(SupplierPrincipal caller, ChangePasswordRequest r) {
        Outcome<SessionResponse> outcome = tx.execute(status -> {
            Instant now = Instant.now(clock);
            SupplierUser user = loadCaller(caller);
            String current = r.currentPassword() == null ? "" : r.currentPassword();
            if (!encoder.matches(current, user.getPasswordHash())) {
                user.recordFailedLogin(app.lockout().maxFailures(), now.plus(app.lockout().duration()));
                return Outcome.fail(SupplierPortalError.WRONG_CURRENT_PASSWORD.ex());
            }
            SupplierInputs.password(r.newPassword(), user.getEmail());
            if (encoder.matches(r.newPassword(), user.getPasswordHash())) {
                return Outcome.fail(SupplierPortalError.SAME_PASSWORD.ex());
            }
            user.changePassword(encoder.encode(r.newPassword()), now);
            refreshTokens.revokeAllForUser(user.getId(), SupplierRefreshToken.REASON_PASSWORD, now);
            return Outcome.ok(startSession(user, caller.deviceId() == null ? "unknown" : caller.deviceId(), null,
                    now));
        });
        return outcome.get();
    }

    // ---------------------------------------------------------------- the business

    public MeResponse me(SupplierPrincipal caller) {
        return tx.execute(status -> me(loadCaller(caller)));
    }

    /**
     * Changes the business details while they are still under review or after a rejection. Saving after a
     * rejection sends the business for review again. The email address is the sign-in name and cannot change here.
     */
    public MeResponse updateProfile(SupplierPrincipal caller, long expectedVersion, ProfileRequest r, String ip) {
        SupplierDtos.BusinessDetails details = SupplierInputs.details(r.legalName(), r.tradeName(),
                r.contactPerson(), r.mobile(), r.address(), r.city(), r.state(), r.pinCode(), r.gstin());
        return tx.execute(status -> {
            Instant now = Instant.now(clock);
            SupplierUser user = loadCaller(caller);
            SupplierOrg org = orgs.findForUpdate(caller.orgId()).orElseThrow(SupplierPortalError.ACCOUNT_INACTIVE::ex);
            if (org.getVersion() == null || org.getVersion() != expectedVersion) {
                throw SupplierPortalError.STALE_VERSION.ex();
            }
            if (!SupplierOrg.EDITABLE.contains(org.getStatus())) throw SupplierPortalError.DETAILS_LOCKED.ex();
            org.apply(details);
            org.touch(now);
            event(org, SupplierOrgEvent.DETAILS_UPDATED, user.getId(), org.getStatus(), org.getStatus(), null, ip, now);
            if (SupplierOrg.REJECTED.equals(org.getStatus())) {
                org.resubmit(now);
                event(org, SupplierOrgEvent.RESUBMITTED, user.getId(), SupplierOrg.REJECTED, org.getStatus(), null,
                        ip, now);
            }
            orgs.saveAndFlush(org);
            return me(user);
        });
    }

    // ---------------------------------------------------------------- helpers

    private MeResponse me(SupplierUser user) {
        SupplierOrg org = orgs.findById(user.getSupplierOrgId()).orElseThrow(SupplierPortalError.ACCOUNT_INACTIVE::ex);
        return new MeResponse(SupplierDtos.user(user), SupplierDtos.org(org));
    }

    private SessionResponse startSession(SupplierUser user, String deviceId, String deviceName, Instant now) {
        String raw = Hashing.newOpaqueToken();
        SupplierRefreshToken token = refreshTokens.save(new SupplierRefreshToken(user.getId(), UUID.randomUUID(),
                Hashing.sha256Hex(raw), deviceId, deviceName, now, now.plus(props.refreshTtl())));
        SupplierJwtService.Issued access = jwt.issue(user, deviceId);
        return new SessionResponse(access.token(), access.expiresAt(), raw, token.getExpiresAt(), me(user));
    }

    private void markVerified(SupplierUser user, SupplierOrg org, String ip, Instant now) {
        String from = org.getStatus();
        user.emailVerified(now);
        org.emailVerified(now);
        event(org, SupplierOrgEvent.EMAIL_VERIFIED, user.getId(), from, org.getStatus(), null, ip, now);
    }

    /**
     * A new single-use link that replaces any open one of the same kind; empty when one was sent less than a minute
     * ago (so the endpoints cannot be used to flood a mailbox).
     */
    private Optional<String> newLink(SupplierUser user, String purpose, Instant now) {
        Optional<SupplierEmailToken> last = emailTokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(),
                purpose);
        if (last.isPresent() && last.get().getCreatedAt().plus(props.resendInterval()).isAfter(now)) {
            return Optional.empty();
        }
        emailTokens.retireOpen(user.getId(), purpose, now);
        String raw = Hashing.newOpaqueToken();
        boolean verify = SupplierEmailToken.VERIFY_EMAIL.equals(purpose);
        emailTokens.save(new SupplierEmailToken(user.getId(), purpose, Hashing.sha256Hex(raw), now,
                now.plus(verify ? props.verifyLinkTtl() : props.resetLinkTtl())));
        return Optional.of(props.publicBaseUrl() + (verify ? VERIFY_PATH : RESET_PATH) + raw);
    }

    private SupplierEmailToken emailToken(String raw, String purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 100) throw SupplierPortalError.LINK_INVALID.ex();
        return emailTokens.findForUpdate(Hashing.sha256Hex(raw.trim()))
                .filter(t -> purpose.equals(t.getPurpose()))
                .orElseThrow(SupplierPortalError.LINK_INVALID::ex);
    }

    private static void checkUsable(SupplierEmailToken t, Instant now) {
        if (t.isUsed()) throw SupplierPortalError.LINK_USED.ex();
        if (t.isExpired(now)) throw SupplierPortalError.LINK_EXPIRED.ex();
    }

    private void event(SupplierOrg org, String action, UUID actor, String from, String to, String reason, String ip,
                       Instant now) {
        events.save(new SupplierOrgEvent(org.getId(), action, SupplierOrgEvent.ACTOR_SUPPLIER, actor, from, to, reason,
                ip, now));
    }

    private SupplierUser loadCaller(SupplierPrincipal caller) {
        return users.findById(caller.userId()).filter(SupplierUser::isActive)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Sign in required"));
    }

    private void limit(String ip) {
        if (!rateLimiter.tryAcquire(ip)) throw SupplierPortalError.TOO_MANY_ATTEMPTS.ex();
    }

    private static String deviceId(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > 64) throw SupplierPortalError.DEVICE_REQUIRED.ex();
        return v;
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Lets a transaction commit its side effects (failed-attempt counters) and still report an error afterwards. */
    private record Outcome<T>(T value, ApiException error) {
        static <T> Outcome<T> ok(T value) { return new Outcome<>(value, null); }
        static <T> Outcome<T> fail(ApiException error) { return new Outcome<>(null, error); }

        T get() {
            if (error != null) throw error;
            return value;
        }
    }
}
