package app.stocklist.common.security;

import app.stocklist.common.error.ApiException;
import java.util.Locale;
import java.util.Set;

/** Minimum 8 characters, not a common password, not the username. */
public final class PasswordPolicy {
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890", "qwerty123",
            "qwertyuiop", "11111111", "00000000", "iloveyou", "admin123", "welcome1", "abc12345",
            "letmein1", "india123", "passw0rd", "87654321", "asdfghjk", "zxcvbnm1", "shop1234", "owner123");

    private PasswordPolicy() {}

    public static void validate(String password, String username) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least 8 characters");
        }
        if (password.length() > 128) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at most 128 characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw ApiException.badRequest("WEAK_PASSWORD", "This password is too common");
        }
        if (username != null && lower.equals(username.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must not be the same as the username");
        }
    }
}
