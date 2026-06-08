package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when a password reset token is invalid or already used.
 *
 * <p>Per B1 Baseline §15, this maps to 401 {@code AUTH_PASSWORD_RESET_TOKEN_INVALID}.
 * The exception message must not contain the raw token value.
 */
public class PasswordResetTokenInvalidException extends RuntimeException {

    public PasswordResetTokenInvalidException() {
        super("Password reset token is invalid or has already been used");
    }
}
