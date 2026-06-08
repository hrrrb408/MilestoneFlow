package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when the refresh token has exceeded its TTL.
 *
 * <p>The session may be marked as EXPIRED, or the refresh_expires_at
 * timestamp has passed.
 */
public class RefreshTokenExpiredException extends RuntimeException {

    public RefreshTokenExpiredException() {
        super("Refresh token has expired");
    }
}
