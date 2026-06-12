package com.milestoneflow.workspace.application.port.in;

import com.milestoneflow.workspace.application.result.CurrentWorkspaceMembershipResult;

import java.util.UUID;

/**
 * Use case for retrieving the current user's membership in a workspace.
 *
 * <p>Requires the caller to have an ACTIVE membership in the workspace.
 * Non-members receive 404 (not 403) to prevent workspace existence leakage.
 */
public interface GetCurrentWorkspaceMembershipUseCase {

    /**
     * Gets the current user's membership in a workspace.
     *
     * @param workspaceId the workspace to look up membership for
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID (for audit)
     * @return the current user's membership result
     */
    CurrentWorkspaceMembershipResult getCurrentMembership(UUID workspaceId, UUID userId, String requestId);
}
