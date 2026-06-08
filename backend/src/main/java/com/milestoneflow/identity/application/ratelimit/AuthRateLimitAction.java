package com.milestoneflow.identity.application.ratelimit;

/**
 * Identifies the authentication action being rate-limited.
 *
 * <p>Each action has its own configurable rate limit policy.
 */
public enum AuthRateLimitAction {
    LOGIN,
    REGISTER,
    EMAIL_VERIFICATION_RESEND,
    FORGOT_PASSWORD,
    RESET_PASSWORD
}
