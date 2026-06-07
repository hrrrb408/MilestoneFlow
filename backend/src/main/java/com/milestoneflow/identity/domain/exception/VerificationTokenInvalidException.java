package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when a verification token is invalid, expired, or already used.
 *
 * <p>The exception uses a single error type for all token failure cases
 * to prevent account enumeration and token state leakage.
 * Internal logging may include a reason code, but the public API does not.
 *
 * <p>This exception must not contain the raw token value in its message.
 */
public class VerificationTokenInvalidException extends RuntimeException {

    private final String internalReason;

    public VerificationTokenInvalidException(String internalReason) {
        super("Verification token is invalid or has expired");
        this.internalReason = internalReason;
    }

    /**
     * Returns an internal reason code for logging purposes only.
     * Must not be exposed in API responses.
     */
    public String getInternalReason() {
        return internalReason;
    }
}
