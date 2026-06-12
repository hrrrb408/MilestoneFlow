package com.milestoneflow.milestone.application.port.in;

import com.milestoneflow.milestone.application.result.MilestoneResult;

import java.util.List;
import java.util.UUID;

/**
 * Use case for listing milestones within a project.
 */
public interface ListMilestonesUseCase {

    /**
     * Lists milestones in the specified project, optionally filtered by status.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID (nullable)
     * @param status      optional status filter (nullable)
     * @return list of milestone results
     */
    List<MilestoneResult> listMilestones(UUID workspaceId, UUID projectId,
                                         UUID userId, String requestId,
                                         String status);
}
