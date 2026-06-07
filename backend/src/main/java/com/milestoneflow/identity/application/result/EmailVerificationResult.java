package com.milestoneflow.identity.application.result;

import com.milestoneflow.identity.domain.type.UserStatus;

import java.util.UUID;

/**
 * Result of a successful email verification confirmation.
 */
public record EmailVerificationResult(UUID userId, String email, UserStatus status) {
}
