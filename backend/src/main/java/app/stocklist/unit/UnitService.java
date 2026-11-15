package app.stocklist.unit;

import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.ChangeSequencer;
import app.stocklist.common.sync.VersionConflictException;
import app.stocklist.permission.Permission;
import app.stocklist.unit.UnitDtos.UnitDto;
import app.stocklist.unit.UnitDtos.UnitInput;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Custom units. Creating one needs ITEM_ADD or ITEM_EDIT; deleting needs ITEM_DELETE. Built-in units are fixed. */
@Service
public class UnitService {
    private final UnitRepository units;
    private final ChangeSequencer seq;
    private final AuditService audit;
    private final Clock clock;

    public UnitService(UnitRepository units, ChangeSequencer seq, AuditService audit, Clock clock) {
        this.units = units;
        this.seq = seq;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UnitDto> list(AuthUser caller) {
        return units.usable(caller.businessId()).stream().map(UnitDto::of).toList();
    }

    @Transactional
    public UnitDto create(AuthUser caller, UUID id, UnitInput in) {
        if (!caller.has(Permission.ITEM_ADD) && !caller.has(Permission.ITEM_EDIT)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to add units");
        }
        if (units.existsById(id)) {
            UnitOfMeasure existing = units.findByBusinessIdAndId(caller.businessId(), id)
                    .orElseThrow(() -> ApiException.conflict("ID_IN_USE", "This id is already used"));
            throw new VersionConflictException("VERSION_CONFLICT", "This unit already exists", UnitDto.of(existing),
                    existing.getVersion());
        }
        Instant now = Instant.now(clock);
        UnitOfMeasure u = new UnitOfMeasure(id, caller.businessId(), caller.userId(), in.code().trim().toUpperCase(),
                in.name().trim(), now, seq.next(caller.businessId()));
        try {
            units.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE_UNIT", "A unit with this code already exists");
        }
        audit.record(caller.businessId(), caller.userId(), "UNIT_CREATED", "UNIT", id, Map.of("code", u.getCode()),
                caller.deviceId(), null);
        return UnitDto.of(u);
    }

    @Transactional
    public UnitDto delete(AuthUser caller, UUID id, Long baseVersion) {
        if (!caller.has(Permission.ITEM_DELETE)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission to delete units");
        }
        UnitOfMeasure u = units.findByBusinessIdAndId(caller.businessId(), id)
                .orElseThrow(() -> ApiException.notFound("Unit"));
        VersionConflictException.check(u, baseVersion, UnitDto.of(u));
        u.softDelete(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        units.saveAndFlush(u);
        audit.record(caller.businessId(), caller.userId(), "UNIT_DELETED", "UNIT", id, null, caller.deviceId(), null);
        return UnitDto.of(u);
    }

    /** Used by items and list lines: the unit must be built-in or belong to the caller's business. */
    public UnitOfMeasure requireUsable(UUID businessId, UUID unitId) {
        return units.findUsable(businessId, unitId).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_UNIT", "Unknown unit"));
    }
}
