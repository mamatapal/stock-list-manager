package app.stocklist.app

import app.stocklist.app.core.util.Validators
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test
    fun mobileNumbers() {
        assertNull(Validators.mobile("9876543210"))
        assertNotNull(Validators.mobile("1234567890"))
        assertNotNull(Validators.mobile("98765"))
        assertNull(Validators.mobile("", required = false))
    }

    @Test
    fun pinAndGstin() {
        assertNull(Validators.pinCode("411001"))
        assertNotNull(Validators.pinCode("011001"))
        assertNull(Validators.gstin(""))
        assertNull(Validators.gstin("27AAPFU0939F1ZV"))
        assertNotNull(Validators.gstin("27AAPFU0939F1Z"))
    }

    @Test
    fun usernamesAndPasswords() {
        assertNull(Validators.username("ravi.kumar_2"))
        assertNotNull(Validators.username("ra"))
        assertNotNull(Validators.username("ravi kumar"))
        assertNotNull(Validators.newPassword("short", null))
        assertNotNull(Validators.newPassword("ravikumar", "RaviKumar"))
        assertNull(Validators.newPassword("Green-Mango-42", "ravi"))
    }
}
