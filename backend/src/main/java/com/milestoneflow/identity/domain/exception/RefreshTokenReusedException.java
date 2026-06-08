package com.milestoneflow.identity.domain.exception;

/**
 * Thrown when an already-rotated refresh token is reused (replay detected).
 *
 * <p>Per B1 Authentication Baseline: replay triggers revocation of the
 * entire session family. No new tokens are issued.
 */
public class RefreshTokenReusedException extends RuntimeException {

    public RefreshTokenReusedException() {
        super("Refresh token has been reused");
    }
}
