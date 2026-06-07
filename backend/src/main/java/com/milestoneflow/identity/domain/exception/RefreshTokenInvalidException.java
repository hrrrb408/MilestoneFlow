package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when the refresh token hash does not match any session.
 *
 * <p>This exception does not distinguish between "token never existed"
 * and "token was already deleted" to prevent information leakage.
 */
public class RefreshTokenInvalidException extends RuntimeException {

    public RefreshTokenInvalidException() {
        super("Refresh token is invalid");
    }
}
