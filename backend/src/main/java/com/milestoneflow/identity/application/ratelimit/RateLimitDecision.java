package com.milestoneflow.identity.application.ratelimit;

/**
 * Result of a rate limit check.
 *
 * @param allowed   whether the request is allowed
 * @param remaining remaining attempts in the current window (for internal use only)
 * @param retryAfterSeconds seconds until the limit resets (0 if allowed)
 */
public record RateLimitDecision(
        boolean allowed,
        long remaining,
        long retryAfterSeconds
) {
    /**
     * Creates an allowed decision.
     */
    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, remaining, 0);
    }

    /**
     * Creates a rejected decision.
     */
    public static RateLimitDecision rejected(long retryAfterSeconds) {
        return new RateLimitDecision(false, 0, retryAfterSeconds);
    }
}
