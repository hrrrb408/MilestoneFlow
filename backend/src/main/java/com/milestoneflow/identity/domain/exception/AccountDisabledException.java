package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when attempting to verify an email for a disabled account.
 *
 * <p>This exception should only be raised when we already know the account
 * exists and is disabled (i.e., not for unknown tokens, which use
 * {@link VerificationTokenInvalidException} to prevent enumeration).
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("Account is disabled");
    }
}
