package com.milestoneflow.identity.infrastructure.crypto;

import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.service.SecretToken;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically secure random tokens using {@link SecureRandom}.
 *
 * <p>Produces 32 random bytes (256 bits of entropy) encoded as Base64 URL-safe
 * without padding, suitable for inclusion in verification links.
 *
 * <p>Thread-safe: each invocation uses the shared {@link SecureRandom} instance,
 * which is thread-safe by specification.
 */
@Component
public class SecureRandomTokenGenerator implements SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public SecretToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new SecretToken(encoded);
    }
}
