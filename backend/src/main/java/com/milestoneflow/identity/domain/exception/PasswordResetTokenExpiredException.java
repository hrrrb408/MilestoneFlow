package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when a password reset token has expired.
 *
 * <p>Per B1 Baseline §15, this maps to 401 {@code AUTH_PASSWORD_RESET_TOKEN_EXPIRED}.
 * The exception message must not contain the raw token value.
 */
public class PasswordResetTokenExpiredException extends RuntimeException {

    public PasswordResetTokenExpiredException() {
        super("Password reset token has expired");
    }
}
