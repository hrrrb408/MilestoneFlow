package com.milestoneflow.identity.application.exception;

/**
 * Thrown when a refresh request does not include the MF_REFRESH cookie.
 *
 * <p>Per B1 Authentication Baseline: refresh token is only accepted via
 * HttpOnly cookie — never from body, query string, or Authorization header.
 *
 * <p>This exception lives in the application layer (not domain) because
 * it represents an input validation concern (missing cookie) rather than
 * a domain rule violation.
 */
public class RefreshTokenMissingException extends RuntimeException {

    public RefreshTokenMissingException() {
        super("Refresh token is required");
    }
}
