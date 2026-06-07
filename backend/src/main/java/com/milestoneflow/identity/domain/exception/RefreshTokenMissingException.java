package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when a refresh request does not include the MF_REFRESH cookie.
 *
 * <p>Per B1 Authentication Baseline: refresh token is only accepted via
 * HttpOnly cookie — never from body, query string, or Authorization header.
 */
public class RefreshTokenMissingException extends RuntimeException {

    public RefreshTokenMissingException() {
        super("Refresh token is required");
    }
}
