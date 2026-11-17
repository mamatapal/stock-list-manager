package app.stocklist.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SenderAddressesTest {
    static final String GMAIL = "suppliermanagement.mail@gmail.com";

    @Test
    void theBareAddressIsTakenFromADisplayName() {
        assertThat(SenderAddresses.address("Supplier Management <" + GMAIL + ">", "MAIL_FROM")).isEqualTo(GMAIL);
        assertThat(SenderAddresses.address("  " + GMAIL + "  ", "MAIL_FROM")).isEqualTo(GMAIL);
    }

    @Test
    void theAccountAndItsAliasesAreAcceptedInAnyCase() {
        assertThatCode(() -> SenderAddresses.check(GMAIL, "", "Shop <SupplierManagement.Mail@GMAIL.com>", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> SenderAddresses.check(GMAIL, "orders@yourshop.in,,help@yourshop.in ",
                "Shop <ORDERS@yourshop.in>", "Suppliers <help@yourshop.in>")).doesNotThrowAnyException();
        assertThatCode(() -> SenderAddresses.check(GMAIL, null, "", " ")).doesNotThrowAnyException();
    }

    @Test
    void otherAddressesAreRefusedNamingOnlyTheSetting() {
        assertThatThrownBy(() -> SenderAddresses.check(GMAIL, null, "no-reply@yourshop.in", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("MAIL_FROM must use")
                .hasMessageNotContaining("yourshop");
        assertThatThrownBy(() -> SenderAddresses.check(GMAIL, "orders@yourshop.in", GMAIL, "x@yourshop.in"))
                .hasMessageStartingWith("SUPPLIER_MAIL_FROM must use");
        assertThatThrownBy(() -> SenderAddresses.check(GMAIL, null, "suppliermanagement.mail@gmail.co", null))
                .hasMessageStartingWith("MAIL_FROM must use");
    }

    @Test
    void unparsableValuesAreRefused() {
        assertThatThrownBy(() -> SenderAddresses.check("not-an-address", null, GMAIL, null))
                .hasMessageStartingWith("GMAIL_SENDER");
        assertThatThrownBy(() -> SenderAddresses.check(GMAIL, "a@b.in, broken alias", GMAIL, null))
                .hasMessageStartingWith("GMAIL_SEND_AS_ALIASES");
        assertThatThrownBy(() -> SenderAddresses.check(GMAIL, null, GMAIL + ", other@gmail.com", null))
                .hasMessageStartingWith("MAIL_FROM");
    }
}
