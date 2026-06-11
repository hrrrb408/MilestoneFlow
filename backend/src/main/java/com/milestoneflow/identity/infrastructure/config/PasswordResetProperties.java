package com.milestoneflow.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for password reset.
 *
 * <p>Example configuration:
 * <pre>
 * milestoneflow:
 *   auth:
 *     password-reset:
 *       token-ttl: PT1H
 * </pre>
 *
 * <p>Per B1 Baseline §9.2, the default TTL is 1 hour.
 * This configuration does not contain secrets.
 */
@ConfigurationProperties(prefix = "milestoneflow.auth.password-reset")
public record PasswordResetProperties(Duration tokenTtl) {

    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    public PasswordResetProperties {
        if (tokenTtl == null) {
            tokenTtl = DEFAULT_TTL;
        }
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "milestoneflow.auth.password-reset.token-ttl must be positive");
        }
    }
}
