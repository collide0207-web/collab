package app.collide.control.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque-token helpers. Refresh/reset/verification tokens are high-entropy random
 * strings handed to the client; only their SHA-256 hash is persisted, so a DB leak
 * yields nothing replayable. SHA-256 (not BCrypt) is correct here: these tokens are
 * already 256-bit random, so there's nothing to brute-force and we want fast lookups.
 */
public final class Tokens {

    private static final SecureRandom RNG = new SecureRandom();

    private Tokens() {}

    /** 256 bits of cryptographic randomness, URL-safe, no padding. */
    public static String generate() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** Lowercase hex SHA-256 of the input — the value stored in the DB. */
    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
