package com.milestoneflow.identity.application.exception;

/**
 * Thrown when an authentication action is rejected due to rate limiting.
 *
 * <p>Mapped to HTTP 429 with error code {@code AUTH_RATE_LIMITED}.
 * The response does not expose which limit was hit, the key used,
 * or any internal counter state.
 */
public class AuthRateLimitedException extends RuntimeException {

    public AuthRateLimitedException() {
        super("Too many requests. Please try again later.");
    }
}
