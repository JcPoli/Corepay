package dev.jcpolicarpio.corepay.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Hash of a request body, stored alongside an Idempotency-Key.
 *
 * A retry of the same key with the same body must return the original
 * response; the same key with a *different* body is a client bug and is
 * rejected, because silently treating it as a duplicate would lose a payment.
 */
public final class IdempotencyFingerprint {

    private IdempotencyFingerprint() {
    }

    public static String of(String payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}
