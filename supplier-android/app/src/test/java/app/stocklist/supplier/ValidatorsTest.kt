package app.stocklist.supplier

import app.stocklist.supplier.core.util.Validators
import app.stocklist.supplier.feature.account.BusinessFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's checks match the server's (SupplierInputs), so mistakes are shown before anything is sent. */
class ValidatorsTest {
    @Test
    fun mobileNumbers() {
        assertEquals("9876543210", Validators.cleanMobile("+91 98765-43210"))
        assertEquals("9876543210", Validators.cleanMobile("09876543210"))
        assertNull(Validators.mobile("98765 43210"))
        assertNotNull(Validators.mobile("5876543210"))
        assertNotNull(Validators.mobile("98765"))
    }

    @Test
    fun gstinPinEmailAndPasswords() {
        assertNull(Validators.gstin(""))
        assertNull(Validators.gstin("27aapfu0939f1zv"))
        assertNotNull(Validators.gstin("27AAPFU0939F1Z"))
        assertNull(Validators.pinCode("452001"))
        assertNotNull(Validators.pinCode("052001"))
        assertNull(Validators.email("a.b@example.in"))
        assertNotNull(Validators.email("a@b"))
        assertNull(Validators.password("Blue-Kettle-77"))
        assertNotNull(Validators.password("short"))
        assertNotNull(Validators.confirm("one-password", "another-one"))
    }

    @Test
    fun theFormCleansWhatIsSent() {
        val form = BusinessFormState()
        assertFalse(form.check())
        assertTrue(form.errors.keys.containsAll(listOf("legalName", "contactPerson", "mobile", "pinCode")))
        form.legalName = " अग्रवाल ट्रेडर्स "
        form.contactPerson = "Ramesh"
        form.mobile = "+91 98765 43210"
        form.address = "12 Station Road"
        form.city = "Indore"
        form.state = "Madhya Pradesh"
        form.pinCode = "452001"
        form.gstin = "23aapca1234b1z5"
        assertTrue(form.check())
        val p = form.toProfile()
        assertEquals("अग्रवाल ट्रेडर्स", p.legalName)
        assertEquals("9876543210", p.mobile)
        assertEquals("23AAPCA1234B1Z5", p.gstin)
        assertNull(p.tradeName)
    }
}
