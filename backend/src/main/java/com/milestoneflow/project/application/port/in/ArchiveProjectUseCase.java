package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.result.ProjectResult;

import java.util.UUID;

/**
 * Use case port for archiving a project.
 */
public interface ArchiveProjectUseCase {

    /**
     * Archives a project in the specified workspace.
     *
     * <p>Only workspace OWNER can archive. Project must be in ACTIVE status.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project to archive
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID
     * @return the archived project result
     */
    ProjectResult archive(UUID workspaceId, UUID projectId, UUID userId, String requestId);
}
