package com.milestoneflow.workspace.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model representing the current user's membership in a workspace.
 *
 * <p>Carries only the caller's own role and status — no email/displayName and
 * no sensitive fields. Used by the {@code GET /workspaces/{id}/members/me}
 * endpoint.
 *
 * @param workspaceId the workspace context
 * @param userId      the calling user
 * @param role        the caller's membership role (e.g., "OWNER")
 * @param status      the caller's membership status (e.g., "ACTIVE")
 * @param joinedAt    when the caller's membership became ACTIVE
 */
public record CurrentWorkspaceMembershipResult(
        UUID workspaceId,
        UUID userId,
        String role,
        String status,
        Instant joinedAt
) {
}
