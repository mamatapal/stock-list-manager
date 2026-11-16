package app.stocklist.platformadmin;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time codes (RFC 6238: HMAC-SHA1, 30-second steps, 6 digits) as shown by authenticator apps such as
 * Google Authenticator or Microsoft Authenticator. Used only for portal administrators.
 */
public final class Totp {
    public static final int STEP_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    /** 160 random bits, Base32 (what an authenticator app asks for when the key is typed in). */
    public static String newSecret() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    /** The code for one time step. */
    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(fromBase32(base32Secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The time step a code belongs to (now, or one step either side for clock drift), or -1. A step at or before
     * {@code lastUsedStep} is refused, so a code can be used only once.
     */
    public static long match(String base32Secret, String code, Instant now, long lastUsedStep) {
        if (code == null) return -1;
        String c = code.replace(" ", "");
        if (!c.matches("[0-9]{6}")) return -1;
        long current = step(now);
        for (long s = current - 1; s <= current + 1; s++) {
            if (s <= lastUsedStep) continue;
            if (MessageDigest.isEqual(code(base32Secret, s).getBytes(), c.getBytes())) return s;
        }
        return -1;
    }

    /** otpauth:// address an authenticator app can import (also printed for typing in by hand). */
    public static String uri(String issuer, String account, String base32Secret) {
        String i = issuer.replace(" ", "%20");
        return "otpauth://totp/" + i + ":" + account + "?secret=" + base32Secret + "&issuer=" + i
                + "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] fromBase32(String s) {
        String v = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(v.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char ch : v.toCharArray()) {
            int val = BASE32.indexOf(ch);
            if (val < 0) throw new IllegalArgumentException("Not Base32");
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }
}
