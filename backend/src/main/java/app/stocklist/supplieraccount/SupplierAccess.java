package app.stocklist.supplieraccount;

import org.springframework.stereotype.Component;

/**
 * Gate for supplier business operations (catalogue, connections, orders in later phases): only an APPROVED
 * organisation may use them. Checked from the database on every call, so a suspension takes effect immediately.
 */
@Component
public class SupplierAccess {
    private final SupplierOrgRepository orgs;

    public SupplierAccess(SupplierOrgRepository orgs) {
        this.orgs = orgs;
    }

    public SupplierOrg requireApproved(SupplierPrincipal caller) {
        SupplierOrg org = orgs.findById(caller.orgId()).orElseThrow(SupplierPortalError.ACCOUNT_INACTIVE::ex);
        if (SupplierOrg.SUSPENDED.equals(org.getStatus())) throw SupplierPortalError.SUPPLIER_SUSPENDED.ex();
        if (!org.isApproved()) throw SupplierPortalError.SUPPLIER_NOT_APPROVED.ex();
        return org;
    }
}
