package com.milestoneflow.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for authentication token lifetimes.
 *
 * <p>Example configuration:
 * <pre>
 * milestoneflow:
 *   auth:
 *     access-token-ttl: PT15M
 *     refresh-token-ttl: P30D
 * </pre>
 *
 * <p>This configuration does not contain secrets.
 */
@ConfigurationProperties(prefix = "milestoneflow.auth")
public record AuthTokenProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {

    private static final Duration DEFAULT_ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(30);

    public AuthTokenProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = DEFAULT_ACCESS_TTL;
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = DEFAULT_REFRESH_TTL;
        }
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "milestoneflow.auth.access-token-ttl must be positive");
        }
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "milestoneflow.auth.refresh-token-ttl must be positive");
        }
        if (refreshTokenTtl.compareTo(accessTokenTtl) < 0) {
            throw new IllegalArgumentException(
                    "milestoneflow.auth.refresh-token-ttl must be >= access-token-ttl");
        }
    }
}
