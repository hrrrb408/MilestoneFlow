package com.milestoneflow.workspace.application.port.in;

import com.milestoneflow.workspace.application.result.WorkspaceResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Use case for retrieving the current user's active workspace.
 *
 * <p>Returns the workspace where the user has an ACTIVE membership.
 * Returns empty if the user has no workspace — does not auto-create.
 */
public interface GetCurrentWorkspaceUseCase {

    /**
     * Finds the current user's active workspace.
     *
     * @param userId the authenticated user
     * @return the workspace result, or empty if none exists
     */
    Optional<WorkspaceResult> getCurrentWorkspace(UUID userId);
}
