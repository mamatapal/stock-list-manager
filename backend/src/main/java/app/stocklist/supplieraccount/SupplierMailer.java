package app.stocklist.supplieraccount;

/** Emails sent to suppliers. Links are complete https addresses; the token sits after "#" so it never reaches logs. */
public interface SupplierMailer {
    void verificationLink(SupplierUser user, String link);

    void passwordResetLink(SupplierUser user, String link);

    /** Someone tried to register again with an address that already has an account. */
    void alreadyRegistered(SupplierUser user, String resetLink);

    /** Security notice to the registered email: the password was reset or changed, all other phones signed out. */
    void passwordChanged(SupplierUser user);

    /** The administrators approved, rejected, suspended or reinstated the business. */
    void statusChanged(SupplierUser user, SupplierOrg org);
}
