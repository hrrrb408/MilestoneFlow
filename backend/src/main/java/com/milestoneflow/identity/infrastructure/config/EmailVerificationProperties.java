package com.milestoneflow.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for email verification.
 *
 * <p>Example configuration:
 * <pre>
 * milestoneflow:
 *   auth:
 *     email-verification:
 *       token-ttl: PT24H
 * </pre>
 *
 * <p>This configuration does not contain secrets.
 */
@ConfigurationProperties(prefix = "milestoneflow.auth.email-verification")
public record EmailVerificationProperties(Duration tokenTtl) {

    /**
     * Default token TTL: 24 hours.
     */
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public EmailVerificationProperties {
        if (tokenTtl == null) {
            tokenTtl = DEFAULT_TTL;
        }
        if (tokenTtl.isNegativeOrZero()) {
            throw new IllegalArgumentException(
                    "milestoneflow.auth.email-verification.token-ttl must be positive");
        }
    }
}
