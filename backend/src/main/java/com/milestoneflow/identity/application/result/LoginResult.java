package com.milestoneflow.identity.application.result;

import com.milestoneflow.identity.application.service.SecretToken;

import java.util.UUID;

/**
 * Result of a successful login.
 *
 * <p>Contains both safe user data and the raw tokens needed by the
 * controller layer to set cookies. Raw tokens must never be logged,
 * persisted, or included in API response bodies.
 *
 * <p>Status is stored as String to avoid leaking domain types to the API layer.
 */
public record LoginResult(
        UUID userId,
        String email,
        String displayName,
        String status,
        SecretToken rawAccessToken,
        SecretToken rawRefreshToken
) {

    @Override
    public String toString() {
        return "LoginResult{userId=" + userId
                + ", email='" + email + "'"
                + ", displayName='" + displayName + "'"
                + ", status=" + status
                + ", rawAccessToken=[REDACTED]"
                + ", rawRefreshToken=[REDACTED]}";
    }
}
