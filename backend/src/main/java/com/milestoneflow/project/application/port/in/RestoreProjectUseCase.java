package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.result.ProjectResult;

import java.util.UUID;

/**
 * Use case port for restoring an archived project.
 */
public interface RestoreProjectUseCase {

    /**
     * Restores an archived project in the specified workspace.
     *
     * <p>Only workspace OWNER can restore. Project must be in ARCHIVED status.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project to restore
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID
     * @return the restored project result
     */
    ProjectResult restore(UUID workspaceId, UUID projectId, UUID userId, String requestId);
}
