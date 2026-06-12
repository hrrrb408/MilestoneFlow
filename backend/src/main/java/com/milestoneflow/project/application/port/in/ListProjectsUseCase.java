package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.result.ProjectResult;

import java.util.List;
import java.util.UUID;

/**
 * Use case port for listing projects in a workspace.
 */
public interface ListProjectsUseCase {

    /**
     * Lists active projects in the specified workspace.
     *
     * @param workspaceId the workspace to list projects for
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID
     * @return list of project results
     */
    List<ProjectResult> listActiveProjects(UUID workspaceId, UUID userId, String requestId);
}
