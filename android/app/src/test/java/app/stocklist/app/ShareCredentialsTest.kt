package app.stocklist.app

import app.stocklist.app.core.util.ShareCredentials
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCredentialsTest {
    @Test
    fun messageContainsTheDetailsAndTheStaffSignInHint() {
        val m = ShareCredentials.message("Test Kirana", "Ravi", "ravi.k", "Abc123xyzPQR")
        assertTrue(m.contains("Test Kirana"))
        assertTrue(m.contains("Username: ravi.k"))
        assertTrue(m.contains("Temporary password: Abc123xyzPQR"))
        assertTrue(m.contains("Staff sign-in"))
    }
}
