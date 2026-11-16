package app.stocklist.platformadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TotpTest {
    /** RFC 6238 appendix B, SHA-1 secret "12345678901234567890" (last 6 of the 8-digit values). */
    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfcTestVectors() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", RFC_SECRET);
        assertEquals("287082", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59))));
        assertEquals("081804", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109))));
        assertEquals("050471", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111111))));
        assertEquals("005924", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890))));
        assertEquals("279037", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000))));
    }

    @Test
    void acceptsOneStepOfClockDriftAndNeverTheSameStepTwice() {
        String secret = Totp.newSecret();
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);
        assertEquals(step, Totp.match(secret, Totp.code(secret, step), now, 0));
        assertEquals(step - 1, Totp.match(secret, Totp.code(secret, step - 1), now, 0));
        assertEquals(step + 1, Totp.match(secret, Totp.code(secret, step + 1), now, 0));
        assertEquals(-1, Totp.match(secret, Totp.code(secret, step - 2), now, 0));
        assertEquals(-1, Totp.match(secret, Totp.code(secret, step), now, step), "already used");
        assertEquals(-1, Totp.match(secret, "12345", now, 0));
        assertEquals(-1, Totp.match(secret, null, now, 0));
        String spaced = Totp.code(secret, step).substring(0, 3) + " " + Totp.code(secret, step).substring(3);
        assertEquals(step, Totp.match(secret, spaced, now, 0));
    }

    @Test
    void base32RoundTripsAndSecretsAreRandom() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, (byte) 250, (byte) 128, 0, 77, 9};
        assertEquals(Base64.getEncoder().encodeToString(data),
                Base64.getEncoder().encodeToString(Totp.fromBase32(Totp.base32(data))));
        assertNotEquals(Totp.newSecret(), Totp.newSecret());
        assertEquals(32, Totp.newSecret().length());
        assertTrue(Totp.uri("Supplier Administration", "admin", "ABC").startsWith(
                "otpauth://totp/Supplier%20Administration:admin?secret=ABC"));
    }

    @Test
    void secretsAreEncryptedWithTheConfiguredKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        SecretCipher cipher = new SecretCipher(new AdminProperties(key, Duration.ofMinutes(30), Duration.ofHours(8), true));
        String enc = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertTrue(enc.startsWith("v1:"));
        assertNotEquals(enc, cipher.encrypt("JBSWY3DPEHPK3PXP"), "random IV each time");
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(enc));
        String other = Base64.getEncoder().encodeToString("another-key-another-key-another-".getBytes());
        SecretCipher wrong = new SecretCipher(new AdminProperties(other, null, null, null));
        assertThrows(IllegalStateException.class, () -> wrong.decrypt(enc));
        assertThrows(IllegalStateException.class,
                () -> new SecretCipher(new AdminProperties(null, null, null, null)));
    }
}
