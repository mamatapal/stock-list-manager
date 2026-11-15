package app.stocklist.common.audit;

import app.stocklist.common.util.Json;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final ActivityLogRepository repository;
    private final Clock clock;

    public AuditService(ActivityLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Written inside the caller's transaction, so the log row and the change commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID businessId, UUID userId, String action, String recordType, Object recordId,
                       Map<String, ?> details, String deviceId, String ip) {
        repository.save(new ActivityLog(businessId, userId, action, recordType,
                recordId == null ? null : recordId.toString(), Json.of(details), deviceId, ip,
                Instant.now(clock)));
    }

    /** For events that must be kept even when the surrounding work fails (e.g. failed logins). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(UUID businessId, UUID userId, String action, String recordType, Object recordId,
                                    Map<String, ?> details, String deviceId, String ip) {
        record(businessId, userId, action, recordType, recordId, details, deviceId, ip);
    }
}
