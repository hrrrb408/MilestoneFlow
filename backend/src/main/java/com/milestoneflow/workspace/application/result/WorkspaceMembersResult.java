package com.milestoneflow.workspace.application.result;

import java.util.List;
import java.util.UUID;

/**
 * Read model representing the member roster of a workspace.
 *
 * <p>Carries only ACTIVE members, ordered by {@code joinedAt} ascending.
 * The list is a read-side projection (per ADR-BE-007) and excludes all
 * REMOVED/PENDING memberships.
 *
 * @param workspaceId the workspace the members belong to
 * @param members     the ACTIVE members, ordered by {@code joinedAt} ascending
 */
public record WorkspaceMembersResult(
        UUID workspaceId,
        List<WorkspaceMemberResult> members
) {
}
