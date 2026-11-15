package app.stocklist.activity;

import app.stocklist.common.audit.ActivityLog;
import app.stocklist.common.audit.ActivityLogRepository;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.security.CurrentUser;
import app.stocklist.user.AppUser;
import app.stocklist.user.AppUserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only, read-only view of the audit log. No endpoint can change or delete entries. */
@RestController
@RequestMapping("/api/v1/activity")
@Tag(name = "Activity log", description = "Owner only, read-only")
@PreAuthorize("@perm.owner()")
public class ActivityController {
    private final ActivityLogRepository logs;
    private final AppUserRepository users;

    public ActivityController(ActivityLogRepository logs, AppUserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    public record ActivityEntry(long id, UUID userId, String username, String action, String recordType,
                                String recordId, String details, String deviceId, Instant createdAt) {}

    public record ActivityPage(List<ActivityEntry> items, int page, int size, long total) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ActivityPage list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        AuthUser caller = CurrentUser.get();
        int safeSize = Math.max(1, Math.min(size, 200));
        Page<ActivityLog> result = logs.findByBusinessIdOrderByCreatedAtDescIdDesc(caller.businessId(),
                PageRequest.of(Math.max(0, page), safeSize));
        Map<UUID, String> names = new HashMap<>();
        List<ActivityEntry> items = result.getContent().stream().map(l -> new ActivityEntry(l.getId(), l.getUserId(),
                l.getUserId() == null ? null : names.computeIfAbsent(l.getUserId(),
                        id -> users.findByBusinessIdAndId(caller.businessId(), id).map(AppUser::getUsername).orElse(null)),
                l.getAction(), l.getRecordType(), l.getRecordId(), l.getDetails(), l.getDeviceId(), l.getCreatedAt()))
                .toList();
        return new ActivityPage(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
