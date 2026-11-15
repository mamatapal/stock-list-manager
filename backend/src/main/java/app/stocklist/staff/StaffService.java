package app.stocklist.staff;

import app.stocklist.auth.AuthService;
import app.stocklist.auth.RefreshToken;
import app.stocklist.auth.RefreshTokenRepository;
import app.stocklist.common.audit.AuditAction;
import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.util.Hashing;
import app.stocklist.permission.Permission;
import app.stocklist.permission.UserPermission;
import app.stocklist.permission.UserPermissionRepository;
import app.stocklist.staff.StaffDtos.CreateStaffRequest;
import app.stocklist.staff.StaffDtos.CreateStaffResponse;
import app.stocklist.staff.StaffDtos.PermissionsResponse;
import app.stocklist.staff.StaffDtos.SessionInfo;
import app.stocklist.staff.StaffDtos.StaffResponse;
import app.stocklist.staff.StaffDtos.TemporaryPasswordResponse;
import app.stocklist.staff.StaffDtos.UpdateStaffRequest;
import app.stocklist.user.AppUser;
import app.stocklist.user.AppUserRepository;
import app.stocklist.user.Role;
import app.stocklist.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff management. Callers need STAFF_MANAGE (checked in the controller); mutations also need a step-up token.
 * Escalation rules: a staff manager can never act on the owner or on themselves, and can only grant or
 * revoke permissions they hold themselves.
 */
@Service
public class StaffService {
    private final AppUserRepository users;
    private final UserPermissionRepository permissions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;

    public StaffService(AppUserRepository users, UserPermissionRepository permissions,
                        RefreshTokenRepository refreshTokens, PasswordEncoder encoder, AuditService audit,
                        Clock clock) {
        this.users = users;
        this.permissions = permissions;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list(AuthUser caller) {
        return users.findByBusinessIdAndRoleOrderByFullNameAsc(caller.businessId(), Role.STAFF).stream()
                .map(StaffResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse get(AuthUser caller, UUID id) {
        return StaffResponse.of(loadStaff(caller, id));
    }

    @Transactional
    public CreateStaffResponse create(AuthUser caller, CreateStaffRequest r, String ip) {
        String username = AuthService.normaliseUsername(r.username());
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "This username is already in use");
        }
        Set<Permission> grant;
        if (r.permissions() == null) {
            grant = EnumSet.copyOf(Permission.staffDefaults());
            if (!caller.isOwner()) {
                grant.retainAll(caller.permissions());
            }
        } else {
            grant = parse(r.permissions());
            requireCanChange(caller, grant);
        }
        Instant now = Instant.now(clock);
        String temporary = Hashing.temporaryPassword();
        AppUser staff = users.save(new AppUser(caller.businessId(), username, r.fullName().trim(),
                blankToNull(r.mobile()), null, encoder.encode(temporary), Role.STAFF, true, caller.userId(), now));
        for (Permission p : grant) {
            permissions.save(new UserPermission(caller.businessId(), staff.getId(), p, caller.userId(), now));
        }
        List<String> codes = sorted(grant);
        audit.record(caller.businessId(), caller.userId(), AuditAction.STAFF_CREATED, "USER", staff.getId(),
                Map.of("username", username, "permissions", codes), caller.deviceId(), ip);
        return new CreateStaffResponse(StaffResponse.of(staff), codes, temporary);
    }

    @Transactional
    public StaffResponse update(AuthUser caller, UUID id, UpdateStaffRequest r, String ip) {
        AppUser staff = loadStaff(caller, id);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!staff.getFullName().equals(r.fullName().trim())) changes.put("fullName", r.fullName().trim());
        String mobile = blankToNull(r.mobile());
        if (!java.util.Objects.equals(staff.getMobile(), mobile)) changes.put("mobile", mobile);
        staff.updateDetails(r.fullName().trim(), mobile, staff.getEmail(), Instant.now(clock));
        audit.record(caller.businessId(), caller.userId(), AuditAction.STAFF_UPDATED, "USER", staff.getId(), changes,
                caller.deviceId(), ip);
        return StaffResponse.of(staff);
    }

    @Transactional
    public StaffResponse setActive(AuthUser caller, UUID id, boolean active, String ip) {
        AppUser staff = loadStaff(caller, id);
        requireNotSelf(caller, staff);
        Instant now = Instant.now(clock);
        UserStatus target = active ? UserStatus.ACTIVE : UserStatus.INACTIVE;
        if (staff.getStatus() != target) {
            staff.setStatus(target, now);
            if (!active) {
                refreshTokens.revokeAllForUser(caller.businessId(), staff.getId(), RefreshToken.REASON_INACTIVE, now);
            }
            audit.record(caller.businessId(), caller.userId(),
                    active ? AuditAction.STAFF_ACTIVATED : AuditAction.STAFF_DEACTIVATED, "USER", staff.getId(), null,
                    caller.deviceId(), ip);
        }
        return StaffResponse.of(staff);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(AuthUser caller, UUID id, String ip) {
        AppUser staff = loadStaff(caller, id);
        requireNotSelf(caller, staff);
        Instant now = Instant.now(clock);
        String temporary = Hashing.temporaryPassword();
        staff.changePassword(encoder.encode(temporary), true, now);
        refreshTokens.revokeAllForUser(caller.businessId(), staff.getId(), RefreshToken.REASON_PASSWORD, now);
        audit.record(caller.businessId(), caller.userId(), AuditAction.STAFF_PASSWORD_RESET, "USER", staff.getId(),
                null, caller.deviceId(), ip);
        return new TemporaryPasswordResponse(temporary);
    }

    @Transactional(readOnly = true)
    public PermissionsResponse getPermissions(AuthUser caller, UUID id) {
        AppUser staff = loadStaff(caller, id);
        return new PermissionsResponse(staff.getId(), sorted(current(caller.businessId(), staff.getId())));
    }

    @Transactional
    public PermissionsResponse setPermissions(AuthUser caller, UUID id, List<String> requestedCodes, String ip) {
        AppUser staff = loadStaff(caller, id);
        requireNotSelf(caller, staff);
        Set<Permission> requested = parse(requestedCodes);
        List<UserPermission> rows = permissions.findByBusinessIdAndUserId(caller.businessId(), staff.getId());
        Set<Permission> before = EnumSet.noneOf(Permission.class);
        rows.forEach(row -> before.add(row.permission()));

        Set<Permission> added = EnumSet.copyOf(requested);
        added.removeAll(before);
        Set<Permission> removed = EnumSet.copyOf(before);
        removed.removeAll(requested);
        Set<Permission> changed = EnumSet.copyOf(added);
        changed.addAll(removed);
        requireCanChange(caller, changed);

        Instant now = Instant.now(clock);
        for (UserPermission row : rows) {
            if (removed.contains(row.permission())) {
                permissions.delete(row);
            }
        }
        for (Permission p : added) {
            permissions.save(new UserPermission(caller.businessId(), staff.getId(), p, caller.userId(), now));
        }
        if (!changed.isEmpty()) {
            // Forces the staff member's app to refresh and pick up the new permission list.
            staff.bumpTokenVersion(now);
            audit.record(caller.businessId(), caller.userId(), AuditAction.PERMISSIONS_CHANGED, "USER", staff.getId(),
                    Map.of("before", sorted(before), "after", sorted(requested)), caller.deviceId(), ip);
        }
        return new PermissionsResponse(staff.getId(), sorted(requested));
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(AuthUser caller, UUID id) {
        AppUser staff = loadStaff(caller, id);
        List<SessionInfo> result = new ArrayList<>();
        for (RefreshToken t : refreshTokens.findActive(caller.businessId(), staff.getId(), Instant.now(clock))) {
            result.add(new SessionInfo(t.getFamilyId(), t.getDeviceId(), t.getDeviceName(), t.getIssuedAt(),
                    t.getExpiresAt()));
        }
        return result;
    }

    @Transactional
    public void revokeSessions(AuthUser caller, UUID id, String ip) {
        AppUser staff = loadStaff(caller, id);
        requireNotSelf(caller, staff);
        Instant now = Instant.now(clock);
        int count = refreshTokens.revokeAllForUser(caller.businessId(), staff.getId(), RefreshToken.REASON_ADMIN, now);
        staff.bumpTokenVersion(now);
        audit.record(caller.businessId(), caller.userId(), AuditAction.STAFF_SESSIONS_REVOKED, "USER", staff.getId(),
                Map.of("sessions", count), caller.deviceId(), ip);
    }

    // ------------------------------------------------------------------ rules

    /** Only STAFF of the caller's own business; the owner and other businesses' users look "not found". */
    private AppUser loadStaff(AuthUser caller, UUID id) {
        return users.findByBusinessIdAndId(caller.businessId(), id)
                .filter(u -> u.getRole() == Role.STAFF)
                .orElseThrow(() -> ApiException.notFound("Staff member"));
    }

    private static void requireNotSelf(AuthUser caller, AppUser target) {
        if (caller.userId().equals(target.getId())) {
            throw ApiException.forbidden("CANNOT_CHANGE_SELF", "You cannot change your own access");
        }
    }

    static void requireCanChange(AuthUser caller, Set<Permission> changed) {
        if (!caller.isOwner() && !caller.permissions().containsAll(changed)) {
            throw ApiException.forbidden("CANNOT_GRANT",
                    "You can only grant or remove permissions that you have yourself");
        }
    }

    static Set<Permission> parse(List<String> codes) {
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        for (String code : codes) {
            try {
                set.add(Permission.valueOf(code.trim()));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("UNKNOWN_PERMISSION", "Unknown permission: " + code);
            }
        }
        return set;
    }

    private Set<Permission> current(UUID businessId, UUID userId) {
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        permissions.findByBusinessIdAndUserId(businessId, userId).forEach(p -> set.add(p.permission()));
        return set;
    }

    private static List<String> sorted(Set<Permission> set) {
        return set.stream().map(Enum::name).sorted().toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
