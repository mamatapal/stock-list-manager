package app.stocklist.platformadmin;

import app.stocklist.common.security.LoginRateLimiter;
import app.stocklist.common.util.Hashing;
import app.stocklist.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Administrator sign-in for the Supplier Administration portal: username + password + authenticator-app code.
 * Every failure gives the same answer; 5 failures lock the account for 15 minutes; a code is accepted only once.
 * Administrator accounts are created and reset only from the server command line ({@link AdminCommandLine}).
 */
@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,40}");
    public static final String TOTP_ISSUER = "Supplier Administration";

    private final PlatformAdminRepository admins;
    private final AdminSessionRepository sessions;
    private final SecretCipher cipher;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter rateLimiter;
    private final AdminProperties props;
    private final AppProperties app;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String dummyHash;

    public AdminAuthService(PlatformAdminRepository admins, AdminSessionRepository sessions, SecretCipher cipher,
                            PasswordEncoder encoder, LoginRateLimiter rateLimiter, AdminProperties props,
                            AppProperties app, TransactionTemplate tx, Clock clock) {
        this.admins = admins;
        this.sessions = sessions;
        this.cipher = cipher;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.app = app;
        this.tx = tx;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equaliser-" + Hashing.newOpaqueToken());
    }

    public record SignedIn(String rawSessionId, AdminDtos.AdminMe me) {}

    public SignedIn login(AdminDtos.LoginRequest r, String ip) {
        if (!rateLimiter.tryAcquire(ip)) throw AdminError.TOO_MANY_ATTEMPTS.ex();
        String username = r.username() == null ? "" : r.username().trim().toLowerCase(Locale.ROOT);
        String password = r.password() == null ? "" : r.password();
        Optional<SignedIn> result = tx.execute(status -> {
            Instant now = Instant.now(clock);
            Optional<PlatformAdmin> found = admins.findForUpdate(username);
            if (found.isEmpty()) {
                encoder.matches(password, dummyHash);
                log.warn("Admin sign-in failed: unknown username from {}", ip);
                return Optional.empty();
            }
            PlatformAdmin a = found.get();
            if (a.isLocked(now) || !a.isActive()) {
                log.warn("Admin sign-in refused for {} (locked or inactive) from {}", a.getId(), ip);
                return Optional.empty();
            }
            boolean passwordOk = encoder.matches(password, a.getPasswordHash());
            long step = passwordOk ? Totp.match(cipher.decrypt(a.getTotpSecret()), r.code(), now, a.getTotpLastStep())
                    : -1;
            if (!passwordOk || step < 0) {
                boolean locked = a.recordFailedLogin(app.lockout().maxFailures(), now.plus(app.lockout().duration()));
                log.warn("Admin sign-in failed for {} ({}) from {}{}", a.getId(),
                        passwordOk ? "authenticator code" : "password", ip, locked ? "; account locked" : "");
                return Optional.empty();
            }
            a.usedTotpStep(step);
            a.recordSuccessfulLogin(now);
            String raw = Hashing.newOpaqueToken();
            AdminSession s = sessions.save(new AdminSession(a.getId(), Hashing.sha256Hex(raw), now,
                    now.plus(props.sessionMax()), ip));
            log.info("Admin {} signed in from {}", a.getId(), ip);
            return Optional.of(new SignedIn(raw, new AdminDtos.AdminMe(a.getId(), a.getUsername(), a.getFullName(),
                    s.getExpiresAt())));
        });
        return result.orElseThrow(AdminError.INVALID_CREDENTIALS::ex);
    }

    /** The administrator behind a session cookie, if the session is still valid. Extends the idle timeout. */
    public Optional<AdminPrincipal> authenticate(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank() || rawSessionId.length() > 100) return Optional.empty();
        return tx.execute(status -> {
            Instant now = Instant.now(clock);
            Optional<AdminSession> found = sessions.findBySessionHash(Hashing.sha256Hex(rawSessionId));
            if (found.isEmpty() || !found.get().isUsable(now, props.sessionIdle())) return Optional.empty();
            AdminSession s = found.get();
            Optional<PlatformAdmin> admin = admins.findById(s.getAdminId()).filter(PlatformAdmin::isActive);
            if (admin.isEmpty()) return Optional.empty();
            if (s.getLastSeenAt().plusSeconds(30).isBefore(now)) s.seen(now);
            PlatformAdmin a = admin.get();
            return Optional.of(new AdminPrincipal(a.getId(), a.getUsername(), a.getFullName(), s.getId()));
        });
    }

    public void logout(AdminPrincipal caller) {
        tx.executeWithoutResult(status -> sessions.findById(caller.sessionId()).ifPresent(s -> {
            s.revoke(Instant.now(clock));
            log.info("Admin {} signed out", caller.adminId());
        }));
    }

    public AdminDtos.AdminMe me(AdminPrincipal caller) {
        Instant expires = sessions.findById(caller.sessionId()).map(AdminSession::getExpiresAt).orElse(null);
        return new AdminDtos.AdminMe(caller.adminId(), caller.username(), caller.fullName(), expires);
    }

    // ---------------------------------------------------------------- server command line only

    /** Creates an administrator; returns the authenticator secret, shown once. */
    public String createAdmin(String rawUsername, String fullName, String email, String password) {
        String username = validUsername(rawUsername);
        validPassword(password, username);
        if (fullName == null || fullName.isBlank() || fullName.length() > 120) {
            throw new IllegalArgumentException("Full name is required (at most 120 characters)");
        }
        String secret = Totp.newSecret();
        String hash = encoder.encode(password);
        tx.executeWithoutResult(status -> {
            if (admins.findByUsername(username).isPresent()) {
                throw new IllegalArgumentException("An administrator with this username already exists");
            }
            admins.save(new PlatformAdmin(username, fullName.trim(), email == null || email.isBlank() ? null
                    : email.trim(), hash, cipher.encrypt(secret), Instant.now(clock)));
        });
        return secret;
    }

    /** New password and new authenticator secret (lost phone); signs the administrator out everywhere. */
    public String resetAdmin(String rawUsername, String password) {
        String username = validUsername(rawUsername);
        validPassword(password, username);
        String secret = Totp.newSecret();
        String hash = encoder.encode(password);
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            PlatformAdmin a = admins.findForUpdate(username)
                    .orElseThrow(() -> new IllegalArgumentException("No administrator with this username"));
            a.resetCredentials(hash, cipher.encrypt(secret), now);
            sessions.revokeAllForAdmin(a.getId(), now);
        });
        return secret;
    }

    public void deactivateAdmin(String rawUsername) {
        String username = validUsername(rawUsername);
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            PlatformAdmin a = admins.findForUpdate(username)
                    .orElseThrow(() -> new IllegalArgumentException("No administrator with this username"));
            a.deactivate(now);
            sessions.revokeAllForAdmin(a.getId(), now);
        });
    }

    private static String validUsername(String raw) {
        String u = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(u).matches()) {
            throw new IllegalArgumentException("Username: 3-40 characters, a-z 0-9 . _ -");
        }
        return u;
    }

    private static void validPassword(String password, String username) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Administrator passwords need 12 to 128 characters");
        }
        if (password.toLowerCase(Locale.ROOT).contains(username)) {
            throw new IllegalArgumentException("The password must not contain the username");
        }
    }
}
