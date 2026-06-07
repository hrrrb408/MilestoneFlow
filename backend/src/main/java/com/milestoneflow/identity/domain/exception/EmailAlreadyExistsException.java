package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when attempting to register with an email that already exists.
 *
 * <p>This is an application-level exception that does not depend on HTTP.
 * The API layer maps it to the appropriate HTTP response.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Email is already registered");
    }

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
