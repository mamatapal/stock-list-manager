package app.stocklist.auth;

import app.stocklist.auth.AuthDtos.MeResponse;
import app.stocklist.auth.AuthDtos.SessionResponse;
import app.stocklist.business.Business;
import app.stocklist.business.BusinessRepository;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.JwtService;
import app.stocklist.common.util.Hashing;
import app.stocklist.config.AppProperties;
import app.stocklist.permission.Permission;
import app.stocklist.permission.UserPermission;
import app.stocklist.permission.UserPermissionRepository;
import app.stocklist.user.AppUser;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Creates sessions (access token + refresh-token family) and the "who am I" payload. */
@Service
public class SessionService {
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokens;
    private final DeviceRepository devices;
    private final BusinessRepository businesses;
    private final UserPermissionRepository permissions;
    private final AppProperties props;
    private final Clock clock;

    public SessionService(JwtService jwtService, RefreshTokenRepository refreshTokens, DeviceRepository devices,
                          BusinessRepository businesses, UserPermissionRepository permissions, AppProperties props,
                          Clock clock) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.devices = devices;
        this.businesses = businesses;
        this.permissions = permissions;
        this.props = props;
        this.clock = clock;
    }

    /** Starts a new refresh family for this device (login, registration, password change). */
    @Transactional(propagation = Propagation.MANDATORY)
    public SessionResponse startSession(AppUser user, String deviceId, String deviceName, String appVersion) {
        Instant now = Instant.now(clock);
        devices.findByUserIdAndDeviceId(user.getId(), deviceId)
                .ifPresentOrElse(d -> d.seen(deviceName, appVersion, now),
                        () -> devices.save(new Device(user.getBusinessId(), user.getId(), deviceId, deviceName,
                                appVersion, now)));
        return issue(user, UUID.randomUUID(), deviceId, deviceName, now);
    }

    /** Next link in an existing family (refresh-token rotation). Returns the session and the new token's id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Rotated rotate(AppUser user, RefreshToken old) {
        Instant now = Instant.now(clock);
        String raw = Hashing.newOpaqueToken();
        RefreshToken next = new RefreshToken(user.getBusinessId(), user.getId(), old.getFamilyId(),
                Hashing.sha256Hex(raw), old.getDeviceId(), old.getDeviceName(), now, now.plus(props.refreshTtl()));
        refreshTokens.save(next);
        old.rotatedTo(next.getId(), now);
        devices.findByUserIdAndDeviceId(user.getId(), old.getDeviceId()).ifPresent(d -> d.seen(null, null, now));
        JwtService.IssuedToken access = jwtService.issueAccess(user, old.getDeviceId());
        return new Rotated(new SessionResponse(access.token(), access.expiresAt(), raw, next.getExpiresAt(),
                me(user)));
    }

    private SessionResponse issue(AppUser user, UUID familyId, String deviceId, String deviceName, Instant now) {
        String raw = Hashing.newOpaqueToken();
        RefreshToken token = new RefreshToken(user.getBusinessId(), user.getId(), familyId, Hashing.sha256Hex(raw),
                deviceId, deviceName, now, now.plus(props.refreshTtl()));
        refreshTokens.save(token);
        JwtService.IssuedToken access = jwtService.issueAccess(user, deviceId);
        return new SessionResponse(access.token(), access.expiresAt(), raw, token.getExpiresAt(), me(user));
    }

    public MeResponse me(AppUser user) {
        Business business = businesses.findById(user.getBusinessId())
                .orElseThrow(() -> ApiException.notFound("Business"));
        Set<Permission> effective;
        if (user.isOwner()) {
            effective = Permission.all();
        } else {
            effective = EnumSet.noneOf(Permission.class);
            for (UserPermission p : permissions.findByBusinessIdAndUserId(user.getBusinessId(), user.getId())) {
                effective.add(p.permission());
            }
        }
        List<String> codes = effective.stream().map(Enum::name).sorted().toList();
        return new MeResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole().name(),
                business.getId(), business.getName(), user.isMustChangePassword(), codes);
    }

    public record Rotated(SessionResponse session) {}
}
