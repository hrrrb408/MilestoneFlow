package com.milestoneflow.identity.application.port.out;

import com.milestoneflow.identity.application.ratelimit.AuthRateLimitAction;
import com.milestoneflow.identity.application.ratelimit.RateLimitDecision;

/**
 * Rate limiter port for authentication actions.
 *
 * <p>Uses fixed-window counters with configurable max attempts and window duration.
 * Keys are hashed or masked — never raw emails or tokens.
 *
 * <p>V0.1: in-memory implementation. Not distributed. Resets on restart.
 */
public interface AuthRateLimiter {

    /**
     * Checks whether the action is allowed for the given key.
     * If allowed, the counter is incremented.
     *
     * @param action the authentication action being rate-limited
     * @param key    a hashed or masked identifier (never raw email/token)
     * @return the rate limit decision
     */
    RateLimitDecision check(AuthRateLimitAction action, String key);

    /**
     * Resets the counter for the given action and key.
     * Used e.g. to clear login failure count on successful login.
     *
     * @param action the action to reset
     * @param key    the key to reset
     */
    void reset(AuthRateLimitAction action, String key);

    /**
     * Records a failed attempt for the given action and key.
     * Separate from check() to allow pre-checking before expensive operations.
     *
     * @param action the action that failed
     * @param key    the key to record failure for
     */
    void recordFailure(AuthRateLimitAction action, String key);

    /**
     * Removes expired rate limit windows.
     * Should be called periodically to prevent memory growth.
     */
    void cleanup();
}
