package com.milestoneflow.workspace.application.port.in;

import com.milestoneflow.workspace.application.result.WorkspaceResult;

import java.util.UUID;

/**
 * Use case for retrieving workspace details by ID.
 *
 * <p>Requires the caller to have an ACTIVE membership in the workspace.
 */
public interface GetWorkspaceUseCase {

    /**
     * Gets workspace details by ID.
     *
     * @param workspaceId the workspace to look up
     * @param userId      the authenticated user (for access check)
     * @return the workspace result
     */
    WorkspaceResult getWorkspace(UUID workspaceId, UUID userId);
}
