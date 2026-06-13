package com.milestoneflow.progress.application.port.in;

import com.milestoneflow.progress.application.result.ProjectProgressResult;

import java.util.UUID;

/**
 * Use case for querying project-level progress.
 *
 * <p>Returns aggregated task counts, milestone counts, and completion rate
 * for a single project. The completion rate is based on task aggregation
 * across all milestones in the project.
 */
public interface GetProjectProgressUseCase {

    /**
     * Retrieves progress for a project.
     *
     * @param workspaceId workspace scope for tenant isolation
     * @param projectId   project to query
     * @param userId      the authenticated user (for access check)
     * @param requestId   request identifier for tracing
     * @return project progress result
     */
    ProjectProgressResult getProjectProgress(UUID workspaceId, UUID projectId,
                                              UUID userId, String requestId);
}
