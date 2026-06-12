package com.milestoneflow.milestone.application.port.in;

import com.milestoneflow.milestone.application.result.MilestoneResult;

import java.util.UUID;

/**
 * Use case for completing a milestone.
 *
 * <p>Requires OWNER role. The milestone must be in OPEN status.
 * After completion, the milestone status becomes COMPLETED with
 * {@code completedAt} and {@code completedBy} populated.
 */
public interface CompleteMilestoneUseCase {

    /**
     * Completes the specified milestone.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param milestoneId the milestone to complete
     * @param userId      the authenticated user performing the action
     * @param requestId   the request correlation ID (nullable)
     * @return the completed milestone result
     */
    MilestoneResult complete(UUID workspaceId, UUID projectId,
                             UUID milestoneId, UUID userId, String requestId);
}
