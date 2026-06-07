package com.milestoneflow.identity.application.port.out;

/**
 * Output port for hashing security tokens.
 *
 * <p>Implementations must use SHA-256 and produce a fixed-length 64-character
 * lowercase hexadecimal string. The implementation must be thread-safe.
 *
 * <p>Raw tokens are never persisted — only their hashes.
 */
public interface TokenHasher {

    /**
     * Hashes the given raw token.
     *
     * @param rawToken the raw token value
     * @return SHA-256 hash as 64-character lowercase hex string
     */
    String hash(String rawToken);
}
