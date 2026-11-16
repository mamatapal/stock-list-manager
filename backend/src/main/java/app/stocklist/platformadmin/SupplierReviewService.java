package app.stocklist.platformadmin;

import app.stocklist.supplieraccount.SupplierMailer;
import app.stocklist.supplieraccount.SupplierOrg;
import app.stocklist.supplieraccount.SupplierOrgEvent;
import app.stocklist.supplieraccount.SupplierOrgEventRepository;
import app.stocklist.supplieraccount.SupplierOrgRepository;
import app.stocklist.supplieraccount.SupplierUser;
import app.stocklist.supplieraccount.SupplierUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reviewing supplier registrations: approve or reject a business waiting for approval, suspend an approved one,
 * reinstate a suspended one. Every decision needs the version the administrator was looking at (If-Match), is written
 * to the supplier's history, and is emailed to the supplier after it is saved.
 */
@Service
public class SupplierReviewService {
    private static final Logger log = LoggerFactory.getLogger(SupplierReviewService.class);

    private final SupplierOrgRepository orgs;
    private final SupplierUserRepository users;
    private final SupplierOrgEventRepository events;
    private final PlatformAdminRepository admins;
    private final SupplierMailer mailer;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SupplierReviewService(SupplierOrgRepository orgs, SupplierUserRepository users,
                                 SupplierOrgEventRepository events, PlatformAdminRepository admins,
                                 SupplierMailer mailer, TransactionTemplate tx, Clock clock) {
        this.orgs = orgs;
        this.users = users;
        this.events = events;
        this.admins = admins;
        this.mailer = mailer;
        this.tx = tx;
        this.clock = clock;
    }

    /** Pending first (oldest submission first) when no filter is given; otherwise the suppliers in that status. */
    public List<AdminDtos.SupplierSummary> list(String status) {
        if (status == null || status.isBlank()) {
            List<SupplierOrg> all = orgs.findAllByOrderByCreatedAtDesc();
            return all.stream()
                    .sorted((a, b) -> Boolean.compare(!SupplierOrg.PENDING_APPROVAL.equals(a.getStatus()),
                            !SupplierOrg.PENDING_APPROVAL.equals(b.getStatus())))
                    .map(SupplierReviewService::summary).toList();
        }
        if (!SupplierOrg.STATUSES.contains(status)) throw AdminError.INVALID_STATUS_FILTER.ex();
        return orgs.findByStatusOrderBySubmittedAtAscCreatedAtAsc(status).stream()
                .map(SupplierReviewService::summary).toList();
    }

    public AdminDtos.SupplierDetail detail(UUID id) {
        return tx.execute(status -> {
            SupplierOrg o = orgs.findById(id).orElseThrow(AdminError.SUPPLIER_NOT_FOUND::ex);
            List<AdminDtos.SupplierUserInfo> people = users.findBySupplierOrgId(id).stream()
                    .map(u -> new AdminDtos.SupplierUserInfo(u.getId(), u.getEmail(), u.getFullName(), u.getMobile(),
                            u.getRole(), u.isEmailVerified(), u.getLastLoginAt()))
                    .toList();
            Map<UUID, String> names = new HashMap<>();
            List<AdminDtos.SupplierEvent> history = events.findBySupplierOrgIdOrderByIdAsc(id).stream()
                    .map(e -> new AdminDtos.SupplierEvent(e.getAction(), e.getActorType(), actorName(e, names),
                            e.getFromStatus(), e.getToStatus(), e.getReason(), e.getCreatedAt()))
                    .toList();
            long sameGstin = o.getGstin() == null ? 0 : orgs.countOtherWithGstin(id, o.getGstin());
            long sameMobile = orgs.countOtherWithMobile(id, o.getMobile());
            return new AdminDtos.SupplierDetail(summary(o), o.getContactPerson(), o.getAddress(), o.getPinCode(),
                    o.getStatusReason(), o.getEmailVerifiedAt(), people, sameGstin, sameMobile, history);
        });
    }

    public AdminDtos.SupplierDetail approve(AdminPrincipal admin, UUID id, long version, String ip) {
        return decide(admin, id, version, null, Set.of(SupplierOrg.PENDING_APPROVAL), SupplierOrg.APPROVED,
                SupplierOrgEvent.APPROVED, false, ip);
    }

    public AdminDtos.SupplierDetail reject(AdminPrincipal admin, UUID id, long version, String reason, String ip) {
        return decide(admin, id, version, reason, Set.of(SupplierOrg.PENDING_APPROVAL), SupplierOrg.REJECTED,
                SupplierOrgEvent.REJECTED, true, ip);
    }

    public AdminDtos.SupplierDetail suspend(AdminPrincipal admin, UUID id, long version, String reason, String ip) {
        return decide(admin, id, version, reason, Set.of(SupplierOrg.APPROVED), SupplierOrg.SUSPENDED,
                SupplierOrgEvent.SUSPENDED, true, ip);
    }

    public AdminDtos.SupplierDetail reinstate(AdminPrincipal admin, UUID id, long version, String ip) {
        return decide(admin, id, version, null, Set.of(SupplierOrg.SUSPENDED), SupplierOrg.APPROVED,
                SupplierOrgEvent.REINSTATED, false, ip);
    }

    private AdminDtos.SupplierDetail decide(AdminPrincipal admin, UUID id, long version, String rawReason,
                                            Set<String> allowedFrom, String to, String action, boolean needsReason,
                                            String ip) {
        String reason = rawReason == null || rawReason.isBlank() ? null : rawReason.strip();
        if (needsReason && reason == null) throw AdminError.REASON_REQUIRED.ex();
        if (reason != null && reason.length() > 500) throw AdminError.REASON_TOO_LONG.ex();
        List<SupplierUser> notify = tx.execute(status -> {
            Instant now = Instant.now(clock);
            SupplierOrg o = orgs.findForUpdate(id).orElseThrow(AdminError.SUPPLIER_NOT_FOUND::ex);
            if (o.getVersion() == null || o.getVersion() != version) throw AdminError.STALE_VERSION.ex();
            if (!allowedFrom.contains(o.getStatus())) throw AdminError.WRONG_STATUS.ex();
            String from = o.getStatus();
            o.review(to, reason, admin.adminId(), now);
            events.save(new SupplierOrgEvent(o.getId(), action, SupplierOrgEvent.ACTOR_ADMIN, admin.adminId(), from,
                    to, reason, ip, now));
            orgs.saveAndFlush(o);
            log.info("Admin {} changed supplier {} from {} to {}", admin.adminId(), o.getId(), from, to);
            return users.findBySupplierOrgId(id).stream().filter(SupplierUser::isActive).toList();
        });
        SupplierOrg saved = orgs.findById(id).orElseThrow();
        notify.forEach(u -> mailer.statusChanged(u, saved));
        return detail(id);
    }

    private String actorName(SupplierOrgEvent e, Map<UUID, String> cache) {
        if (e.getActorId() == null) return null;
        if (SupplierOrgEvent.ACTOR_ADMIN.equals(e.getActorType())) {
            return cache.computeIfAbsent(e.getActorId(),
                    k -> admins.findById(k).map(PlatformAdmin::getFullName).orElse("Administrator"));
        }
        return cache.computeIfAbsent(e.getActorId(),
                k -> users.findById(k).map(SupplierUser::getEmail).orElse("Supplier"));
    }

    static AdminDtos.SupplierSummary summary(SupplierOrg o) {
        return new AdminDtos.SupplierSummary(o.getId(), o.getLegalName(), o.getTradeName(), o.getCity(), o.getState(),
                o.getEmail(), o.getMobile(), o.getGstin(), o.getStatus(), o.getCreatedAt(), o.getSubmittedAt(),
                o.getReviewedAt(), o.getVersion() == null ? 0 : o.getVersion());
    }
}
