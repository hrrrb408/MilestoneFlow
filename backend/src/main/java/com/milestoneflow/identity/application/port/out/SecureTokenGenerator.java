package com.milestoneflow.identity.application.port.out;

import com.milestoneflow.identity.application.service.SecretToken;

/**
 * Output port for generating cryptographically secure random tokens.
 *
 * <p>Implementations must use {@link java.security.SecureRandom} with at least
 * 256 bits of entropy. Tokens are Base64 URL-safe encoded without padding.
 *
 * <p>UUIDs, timestamps, and user IDs must not be used as security tokens.
 */
public interface SecureTokenGenerator {

    /**
     * Generates a new secure random token.
     *
     * @return a new opaque secret token
     */
    SecretToken generate();
}
