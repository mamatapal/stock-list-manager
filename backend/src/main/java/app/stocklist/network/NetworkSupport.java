package app.stocklist.network;

import app.stocklist.business.Business;
import app.stocklist.business.BusinessRepository;
import app.stocklist.common.security.AuthUser;
import app.stocklist.common.sync.Decimals;
import app.stocklist.network.NetworkDtos.EventDto;
import app.stocklist.network.NetworkDtos.ShopSummary;
import app.stocklist.network.NetworkDtos.SupplierSummary;
import app.stocklist.permission.Permission;
import app.stocklist.supplieraccount.SupplierOrg;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared checks and conversions of the supplier network services. */
@Component
public class NetworkSupport {
    private final BusinessRepository businesses;
    private final DirectoryRepository orgs;

    public NetworkSupport(BusinessRepository businesses, DirectoryRepository orgs) {
        this.businesses = businesses;
        this.orgs = orgs;
    }

    /** The shop caller must hold at least one of these permissions (owners hold all). Checked before anything else. */
    public static void requireAny(AuthUser caller, Permission... permissions) {
        for (Permission p : permissions) {
            if (caller.has(p)) return;
        }
        throw NetworkError.PERMISSION_DENIED.ex();
    }

    /** Trimmed text, null when empty; refused when longer than max. */
    public static String text(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > max) throw NetworkError.TEXT_TOO_LONG.ex();
        return v;
    }

    /** The optional reason or note of a request (at most 500 characters). */
    public static String reason(NetworkDtos.ReasonInput in) {
        return in == null ? null : text(in.reason(), 500);
    }

    public static String money(BigDecimal d) {
        return Decimals.money(d);
    }

    public static String qty(BigDecimal d) {
        return Decimals.format(d);
    }

    public SupplierOrg org(UUID id) {
        return orgs.findById(id).orElseThrow();
    }

    public static SupplierSummary summary(SupplierOrg o) {
        String name = o.getTradeName() == null || o.getTradeName().isBlank() ? o.getLegalName() : o.getTradeName();
        return new SupplierSummary(o.getId(), name, o.getLegalName(), o.getCity(), o.getState());
    }

    public SupplierSummary supplierSummary(UUID orgId) {
        return summary(org(orgId));
    }

    public ShopSummary shopSummary(UUID businessId) {
        Business b = businesses.findById(businessId).orElseThrow();
        return new ShopSummary(b.getName(), b.getCity(), b.getState());
    }

    public static EventDto event(SupplierConnectionEvent e, boolean withActor) {
        return new EventDto(e.getActorType(), withActor ? e.getActorId() : null, e.getFromStatus(), e.getToStatus(),
                e.getReason(), null, e.getCreatedAt());
    }

    public static EventDto event(PurchaseOrderEvent e, boolean withActor) {
        return new EventDto(e.getActorType(), withActor ? e.getActorId() : null, e.getFromStatus(), e.getToStatus(),
                e.getReason(), e.getReceiptStatus(), e.getCreatedAt());
    }
}
