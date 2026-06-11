package com.milestoneflow.identity.infrastructure.email;

/**
 * Runtime exception thrown when email delivery fails.
 *
 * <p>This exception wraps the underlying cause without exposing
 * the raw token, recipient email, or full URL in its message.
 * The {@code AFTER_COMMIT} listener catches it and logs a sanitized summary.
 *
 * <p>Message is intentionally generic to prevent token leakage via
 * exception handlers or error responses.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
