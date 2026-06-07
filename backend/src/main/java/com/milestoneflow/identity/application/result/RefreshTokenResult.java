package com.milestoneflow.identity.application.result;

import com.milestoneflow.identity.application.service.SecretToken;

/**
 * Result of a successful refresh token rotation.
 *
 * <p>Contains the new raw tokens for cookie setting by the controller.
 * Raw tokens must never be logged, persisted, or included in API response bodies.
 */
public record RefreshTokenResult(
        SecretToken rawAccessToken,
        SecretToken rawRefreshToken
) {

    @Override
    public String toString() {
        return "RefreshTokenResult{rawAccessToken=[REDACTED], rawRefreshToken=[REDACTED]}";
    }
}
