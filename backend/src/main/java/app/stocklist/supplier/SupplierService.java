package app.stocklist.supplier;

import app.stocklist.common.audit.AuditService;
import app.stocklist.common.error.ApiException;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.ChangeSequencer;
import app.stocklist.common.sync.VersionConflictException;
import app.stocklist.permission.Permission;
import app.stocklist.supplier.SupplierDtos.SupplierDto;
import app.stocklist.supplier.SupplierDtos.SupplierInput;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier rules, used by both the REST API and sync. Suppliers are never hard-deleted: delete is a soft
 * delete (the record stays for historical lists and purchases), and "inactive" hides a supplier from pickers.
 */
@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final ChangeSequencer seq;
    private final AuditService audit;
    private final Clock clock;

    public SupplierService(SupplierRepository suppliers, ChangeSequencer seq, AuditService audit, Clock clock) {
        this.suppliers = suppliers;
        this.seq = seq;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SupplierDto> search(AuthUser caller, String q, boolean includeInactive) {
        require(caller, Permission.SUPPLIER_VIEW);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return suppliers.search(caller.businessId(), like, includeInactive).stream().map(SupplierDto::of).toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto get(AuthUser caller, UUID id) {
        require(caller, Permission.SUPPLIER_VIEW);
        return SupplierDto.of(load(caller, id));
    }

    @Transactional
    public SupplierDto create(AuthUser caller, UUID id, SupplierInput in) {
        require(caller, Permission.SUPPLIER_ADD);
        rejectIfExists(caller, id);
        Supplier s = new Supplier(id, caller.businessId(), caller.userId(), Instant.now(clock),
                seq.next(caller.businessId()));
        s.apply(in);
        suppliers.saveAndFlush(s);
        audit.record(caller.businessId(), caller.userId(), "SUPPLIER_CREATED", "SUPPLIER", id,
                Map.of("name", s.getName()), caller.deviceId(), null);
        return SupplierDto.of(s);
    }

    @Transactional
    public SupplierDto update(AuthUser caller, UUID id, Long baseVersion, SupplierInput in) {
        require(caller, Permission.SUPPLIER_EDIT);
        Supplier s = load(caller, id);
        VersionConflictException.check(s, baseVersion, SupplierDto.of(s));
        if (s.isDeleted()) s.restore();
        s.apply(in);
        s.touch(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        suppliers.saveAndFlush(s);
        audit.record(caller.businessId(), caller.userId(), "SUPPLIER_UPDATED", "SUPPLIER", id,
                Map.of("name", s.getName(), "active", s.isActive()), caller.deviceId(), null);
        return SupplierDto.of(s);
    }

    @Transactional
    public SupplierDto delete(AuthUser caller, UUID id, Long baseVersion) {
        require(caller, Permission.SUPPLIER_DELETE);
        Supplier s = load(caller, id);
        VersionConflictException.check(s, baseVersion, SupplierDto.of(s));
        s.softDelete(caller.userId(), Instant.now(clock), seq.next(caller.businessId()));
        suppliers.saveAndFlush(s);
        audit.record(caller.businessId(), caller.userId(), "SUPPLIER_DELETED", "SUPPLIER", id,
                Map.of("name", s.getName()), caller.deviceId(), null);
        return SupplierDto.of(s);
    }

    /** For other modules: a live supplier of this business, or a clear error. */
    public Supplier requireUsable(UUID businessId, UUID id) {
        return suppliers.findByBusinessIdAndId(businessId, id).filter(s -> !s.isDeleted())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_SUPPLIER", "Unknown supplier"));
    }

    private Supplier load(AuthUser caller, UUID id) {
        return suppliers.findByBusinessIdAndId(caller.businessId(), id)
                .orElseThrow(() -> ApiException.notFound("Supplier"));
    }

    private void rejectIfExists(AuthUser caller, UUID id) {
        if (suppliers.existsById(id)) {
            Supplier existing = suppliers.findByBusinessIdAndId(caller.businessId(), id)
                    .orElseThrow(() -> ApiException.conflict("ID_IN_USE", "This id is already used"));
            throw new VersionConflictException("VERSION_CONFLICT", "This supplier already exists",
                    SupplierDto.of(existing), existing.getVersion());
        }
    }

    static void require(AuthUser caller, Permission p) {
        if (!caller.has(p)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "You do not have permission for this action");
        }
    }
}
