package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when a verification token is invalid, expired, or already used.
 *
 * <p>Per B1 Baseline §15, token errors return 401 with one of two codes:
 * <ul>
 *   <li>{@code AUTH_VERIFICATION_TOKEN_INVALID} — token not found or already used</li>
 *   <li>{@code AUTH_VERIFICATION_TOKEN_EXPIRED} — token has expired</li>
 * </ul>
 *
 * <p>This exception must not contain the raw token value in its message.
 */
public class VerificationTokenInvalidException extends RuntimeException {

    /**
     * Distinguishes the public error code to return.
     */
    public enum Type {
        /** Token not found, already used, or other invalid state. Maps to 401 AUTH_VERIFICATION_TOKEN_INVALID. */
        INVALID,
        /** Token has expired. Maps to 401 AUTH_VERIFICATION_TOKEN_EXPIRED. */
        EXPIRED
    }

    private final Type type;
    private final String internalReason;

    public VerificationTokenInvalidException(Type type, String internalReason) {
        super("Verification token is invalid or has expired");
        this.type = type;
        this.internalReason = internalReason;
    }

    /**
     * Legacy constructor defaulting to INVALID type.
     */
    public VerificationTokenInvalidException(String internalReason) {
        this(Type.INVALID, internalReason);
    }

    public Type getType() {
        return type;
    }

    /**
     * Returns an internal reason code for logging purposes only.
     * Must not be exposed in API responses.
     */
    public String getInternalReason() {
        return internalReason;
    }
}
