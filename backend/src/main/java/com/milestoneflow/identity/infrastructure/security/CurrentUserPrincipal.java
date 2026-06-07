package com.milestoneflow.identity.infrastructure.security;

import java.util.UUID;

/**
 * Authenticated principal stored in the Spring Security context.
 *
 * <p>Contains only safe, non-sensitive fields. Does not include
 * password hashes, token hashes, raw tokens, or version information.
 *
 * <p>Created by the access token authentication filter and used
 * by controllers to resolve the current user without database lookups.
 */
public record CurrentUserPrincipal(
        UUID userId,
        String email,
        String displayName,
        String status,
        UUID sessionId,
        UUID sessionFamilyId
) {
}
