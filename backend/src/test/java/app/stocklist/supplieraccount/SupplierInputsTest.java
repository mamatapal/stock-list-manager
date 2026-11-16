package app.stocklist.supplieraccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.stocklist.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class SupplierInputsTest {
    private static String code(Executable e) {
        return assertThrows(ApiException.class, e).code();
    }

    @Test
    void mobileNumbersAreCleanedAndChecked() {
        assertEquals("9876543210", SupplierInputs.mobile("98765 43210"));
        assertEquals("9876543210", SupplierInputs.mobile("+91-98765-43210"));
        assertEquals("9876543210", SupplierInputs.mobile("09876543210"));
        assertEquals("INVALID_MOBILE", code(() -> SupplierInputs.mobile("5876543210")));
        assertEquals("INVALID_MOBILE", code(() -> SupplierInputs.mobile("98765")));
        assertEquals("INVALID_MOBILE", code(() -> SupplierInputs.mobile(null)));
    }

    @Test
    void gstinPinAndEmail() {
        assertEquals("27AAPFU0939F1ZV", SupplierInputs.gstin(" 27aapfu0939f1zv "));
        assertNull(SupplierInputs.gstin("  "));
        assertEquals("INVALID_GSTIN", code(() -> SupplierInputs.gstin("27AAPFU0939F1Z")));
        assertEquals("452001", SupplierInputs.pinCode("452001"));
        assertEquals("INVALID_PIN_CODE", code(() -> SupplierInputs.pinCode("052001")));
        assertEquals("a.b@example.in", SupplierInputs.email("  A.B@Example.IN "));
        assertEquals("INVALID_EMAIL", code(() -> SupplierInputs.email("a@b")));
        assertEquals("INVALID_EMAIL", code(() -> SupplierInputs.email("a b@example.com")));
    }

    @Test
    void detailsNeedTheMainFields() {
        SupplierDtos.BusinessDetails d = SupplierInputs.details(" अग्रवाल ट्रेडर्स ", "", "Ramesh", "9876543210",
                "12 Road", "Indore", "MP", "452001", null);
        assertEquals("अग्रवाल ट्रेडर्स", d.legalName());
        assertNull(d.tradeName());
        assertEquals("NAME_REQUIRED", code(() -> SupplierInputs.details(null, null, "R", "9876543210", "a", "b",
                "c", "452001", null)));
        assertEquals("CONTACT_REQUIRED", code(() -> SupplierInputs.details("N", null, " ", "9876543210", "a", "b",
                "c", "452001", null)));
        assertEquals("ADDRESS_REQUIRED", code(() -> SupplierInputs.details("N", null, "R", "9876543210", "a", "",
                "c", "452001", null)));
        assertEquals("TEXT_TOO_LONG", code(() -> SupplierInputs.details("N".repeat(151), null, "R", "9876543210",
                "a", "b", "c", "452001", null)));
    }

    @Test
    void passwords() {
        SupplierInputs.password("Blue-Kettle-77", "x@example.com");
        assertEquals("WEAK_PASSWORD", code(() -> SupplierInputs.password("short", null)));
        assertEquals("WEAK_PASSWORD", code(() -> SupplierInputs.password("Password123", null)));
        assertEquals("WEAK_PASSWORD", code(() -> SupplierInputs.password("x@example.com", "x@example.com")));
    }

    @Test
    void everyErrorCodeHasAPlainMessage() {
        for (SupplierPortalError e : SupplierPortalError.values()) {
            assertEquals(e.name(), e.ex().code());
            assertEquals(e.message().strip(), e.message());
        }
    }
}
