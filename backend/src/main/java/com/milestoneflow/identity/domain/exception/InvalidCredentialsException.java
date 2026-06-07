package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when login fails due to wrong email or wrong password.
 *
 * <p>This exception does not distinguish between "email not found" and
 * "wrong password" to prevent account enumeration attacks.
 * Both cases produce the same error code and message.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
