package app.stocklist.platformadmin;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** AES-256-GCM for the administrators' authenticator secrets, with the key from ADMIN_TOTP_KEY. */
@Component
public class SecretCipher {
    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(AdminProperties props) {
        String raw = props.totpKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("ADMIN_TOTP_KEY is not set");
        }
        byte[] k = Base64.getDecoder().decode(raw.trim());
        if (k.length < 32) {
            throw new IllegalStateException("ADMIN_TOTP_KEY must decode to at least 32 bytes");
        }
        this.key = new SecretKeySpec(java.util.Arrays.copyOf(k, 32), "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv)
                    .put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalStateException("Unknown secret format");
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret could not be decrypted (wrong ADMIN_TOTP_KEY?)", e);
        }
    }
}
