package com.milestoneflow.identity.application.result;

import com.milestoneflow.identity.domain.type.UserStatus;

import java.util.UUID;

/**
 * Result of a successful email verification confirmation.
 *
 * <p>Status is stored as String to avoid leaking domain types to the API layer.
 */
public record EmailVerificationResult(UUID userId, String email, String status) {

    public EmailVerificationResult(UUID userId, String email, UserStatus status) {
        this(userId, email, status.name());
    }
}
