package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when a user with PENDING_VERIFICATION status attempts to login.
 *
 * <p>Per B1 Authentication Baseline §5: PENDING_VERIFICATION users cannot login.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address has not been verified");
    }
}
