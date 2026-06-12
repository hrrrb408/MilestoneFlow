package com.milestoneflow.workspace.application.port.in;

import com.milestoneflow.workspace.application.result.WorkspaceMembersResult;

import java.util.UUID;

/**
 * Use case for listing the ACTIVE members of a workspace.
 *
 * <p>Requires the caller to have an ACTIVE membership in the workspace.
 * Non-members receive 404 (not 403) to prevent workspace existence leakage.
 */
public interface ListWorkspaceMembersUseCase {

    /**
     * Lists the ACTIVE members of a workspace.
     *
     * @param workspaceId the workspace whose members to list
     * @param userId      the authenticated user (for access check)
     * @param requestId   the request correlation ID (for audit)
     * @return the workspace members result, ordered by {@code joinedAt} ascending
     */
    WorkspaceMembersResult listMembers(UUID workspaceId, UUID userId, String requestId);
}
