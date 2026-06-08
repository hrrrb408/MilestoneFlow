package com.milestoneflow.identity.application.result;

import java.util.UUID;

/**
 * Result of fetching the current authenticated user.
 *
 * <p>Contains only safe, non-sensitive data suitable for API responses.
 * No password hash, no token hash, no session internals.
 *
 * <p>Status is stored as String to avoid leaking domain types to the API layer.
 */
public record CurrentUserResult(
        UUID userId,
        String email,
        String displayName,
        String status
) {
}
