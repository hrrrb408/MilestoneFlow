package com.milestoneflow.identity.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for authentication rate limiting.
 *
 * <p>V0.1: in-memory only. Not distributed.
 *
 * @param enabled  whether rate limiting is active
 * @param policies per-action rate limit policies
 */
@ConfigurationProperties(prefix = "milestoneflow.auth.rate-limit")
public record AuthRateLimitProperties(
        boolean enabled,
        Map<String, Policy> policies
) {
    /**
     * Default constructor with sensible defaults.
     */
    public AuthRateLimitProperties {
        if (policies == null) {
            policies = Map.of();
        }
    }

    /**
     * Rate limit policy for a specific action.
     *
     * @param maxAttempts maximum allowed attempts in the window
     * @param window      the time window duration
     */
    public record Policy(int maxAttempts, Duration window) {
        public Policy {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be > 0");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("window must be > 0");
            }
        }
    }

    /**
     * Returns the policy for the given action name, or null if not configured.
     */
    public Policy getPolicy(String actionName) {
        return policies.get(actionName);
    }
}
