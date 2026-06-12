package com.milestoneflow.milestone.application.port.in;

import com.milestoneflow.milestone.application.result.MilestoneResult;

import java.util.UUID;

/**
 * Use case for retrieving a single milestone's details.
 */
public interface GetMilestoneUseCase {

    /**
     * Gets a milestone by its composite key (workspace + project + milestone).
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param milestoneId the milestone ID
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID (nullable)
     * @return the milestone result
     */
    MilestoneResult getMilestone(UUID workspaceId, UUID projectId,
                                 UUID milestoneId, UUID userId, String requestId);
}
