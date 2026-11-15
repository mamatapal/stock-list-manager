package app.stocklist.business;

import app.stocklist.business.BusinessDtos.BusinessResponse;
import app.stocklist.business.BusinessDtos.UpdateBusinessRequest;
import app.stocklist.common.audit.AuditAction;
import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {
    private final BusinessRepository businesses;
    private final AuditService audit;
    private final Clock clock;

    public BusinessService(BusinessRepository businesses, AuditService audit, Clock clock) {
        this.businesses = businesses;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessResponse get(AuthUser caller) {
        return BusinessResponse.of(load(caller));
    }

    @Transactional
    public BusinessResponse update(AuthUser caller, UpdateBusinessRequest r, String ip) {
        Business b = load(caller);
        BusinessDetails next = new BusinessDetails(r.name().trim(), r.address().trim(), r.city().trim(),
                r.state().trim(), r.pinCode(), r.phone(), r.email().trim(),
                r.gstin() == null || r.gstin().isBlank() ? null : r.gstin().trim());
        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "name", b.getName(), next.name());
        diff(changes, "address", b.getAddress(), next.address());
        diff(changes, "city", b.getCity(), next.city());
        diff(changes, "state", b.getState(), next.state());
        diff(changes, "pinCode", b.getPinCode(), next.pinCode());
        diff(changes, "phone", b.getPhone(), next.phone());
        diff(changes, "email", b.getEmail(), next.email());
        diff(changes, "gstin", b.getGstin(), next.gstin());
        b.apply(next, Instant.now(clock));
        businesses.flush();
        audit.record(b.getId(), caller.userId(), AuditAction.BUSINESS_UPDATED, "BUSINESS", b.getId(), changes,
                caller.deviceId(), ip);
        return BusinessResponse.of(b);
    }

    private Business load(AuthUser caller) {
        return businesses.findById(caller.businessId()).orElseThrow(() -> ApiException.notFound("Business"));
    }

    private static void diff(Map<String, Object> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, after);
        }
    }
}
