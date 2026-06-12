package com.milestoneflow.workspace.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model representing a single workspace member.
 *
 * <p>Combines {@code workspace_membership} data (role, status, joinedAt) with
 * the safe display fields from {@code app_user} (email, displayName). It is a
 * read-side projection (per ADR-BE-007) and never carries sensitive fields
 * such as {@code passwordHash}, {@code emailNormalized}, or token material.
 *
 * @param userId      the member's app_user ID
 * @param email       the member's raw email address
 * @param displayName the member's human-readable display name
 * @param role        membership role (e.g., "OWNER")
 * @param status      membership status (e.g., "ACTIVE")
 * @param joinedAt    when the membership became ACTIVE
 */
public record WorkspaceMemberResult(
        UUID userId,
        String email,
        String displayName,
        String role,
        String status,
        Instant joinedAt
) {
}
