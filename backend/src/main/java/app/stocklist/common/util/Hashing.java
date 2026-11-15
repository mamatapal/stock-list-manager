package app.stocklist.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class Hashing {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TEMP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private Hashing() {}

    /** 256-bit random token, URL-safe. Only its SHA-256 is ever stored. */
    public static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Readable temporary password (no 0/O/1/l/I) that the owner passes to a staff member. */
    public static String temporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        }
        return sb.toString();
    }
}
