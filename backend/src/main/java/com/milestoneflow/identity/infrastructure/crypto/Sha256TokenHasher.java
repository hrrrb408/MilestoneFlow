package com.milestoneflow.identity.infrastructure.crypto;

import com.milestoneflow.identity.application.port.out.TokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 implementation of {@link TokenHasher}.
 *
 * <p>Produces a fixed-length 64-character lowercase hexadecimal string.
 * Thread-safe: creates a new {@link MessageDigest} per invocation
 * (MessageDigest is not thread-safe for reuse).
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public String hash(String rawToken) {
        MessageDigest digest = createSha256Digest();
        byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }

    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0f]);
            sb.append(HEX_CHARS[b & 0x0f]);
        }
        return sb.toString();
    }
}
