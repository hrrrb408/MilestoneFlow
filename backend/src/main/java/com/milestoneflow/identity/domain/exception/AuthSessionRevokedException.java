package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when the session associated with the refresh token is already
 * revoked for a reason other than rotation.
 *
 * <p>This exception should not trigger family-wide revocation.
 */
public class AuthSessionRevokedException extends RuntimeException {

    public AuthSessionRevokedException() {
        super("Session has been revoked");
    }
}
