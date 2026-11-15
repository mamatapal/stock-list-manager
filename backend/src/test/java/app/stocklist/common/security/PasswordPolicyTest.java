package app.stocklist.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.stocklist.common.error.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void rejectsShortCommonAndUsernamePasswords() {
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("short1", "ravi"));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("Password123", "ravi"));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("ravi.kumar", "ravi.kumar"));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("x".repeat(129), "ravi"));
    }

    @Test
    void acceptsReasonablePassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Green-Mango-42", "ravi"));
    }
}
