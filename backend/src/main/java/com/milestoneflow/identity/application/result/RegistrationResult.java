package com.milestoneflow.identity.application.result;

import com.milestoneflow.identity.domain.type.UserStatus;

import java.util.UUID;

/**
 * Result of a successful user registration.
 *
 * <p>Contains only safe, non-sensitive data suitable for API responses.
 * No password hash, no raw token, no token hash.
 *
 * <p>Status is stored as String to avoid leaking domain types to the API layer.
 */
public record RegistrationResult(UUID userId, String email, String status) {

    public RegistrationResult(UUID userId, String email, UserStatus status) {
        this(userId, email, status.name());
    }
}
