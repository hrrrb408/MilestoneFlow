package com.milestoneflow.milestone.application.port.in;

import com.milestoneflow.milestone.application.result.MilestoneResult;

import java.util.UUID;

/**
 * Use case for reopening a completed milestone.
 *
 * <p>Requires OWNER role. The milestone must be in COMPLETED status.
 * After reopening, the milestone status becomes OPEN with
 * {@code completedAt} and {@code completedBy} cleared.
 */
public interface ReopenMilestoneUseCase {

    /**
     * Reopens the specified milestone.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param milestoneId the milestone to reopen
     * @param userId      the authenticated user performing the action
     * @param requestId   the request correlation ID (nullable)
     * @return the reopened milestone result
     */
    MilestoneResult reopen(UUID workspaceId, UUID projectId,
                           UUID milestoneId, UUID userId, String requestId);
}
