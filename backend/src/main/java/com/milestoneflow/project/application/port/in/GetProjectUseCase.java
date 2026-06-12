package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.result.ProjectResult;

import java.util.UUID;

/**
 * Use case port for retrieving a single project.
 */
public interface GetProjectUseCase {

    /**
     * Gets a project by ID within the specified workspace.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project ID
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID
     * @return the project result
     */
    ProjectResult getProject(UUID workspaceId, UUID projectId, UUID userId, String requestId);
}
