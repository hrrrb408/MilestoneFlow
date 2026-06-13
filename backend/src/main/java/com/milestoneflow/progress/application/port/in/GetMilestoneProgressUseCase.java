package com.milestoneflow.progress.application.port.in;

import com.milestoneflow.progress.application.result.MilestoneProgressResult;

import java.util.UUID;

/**
 * Use case for querying milestone-level progress.
 *
 * <p>Returns aggregated task counts and completion rate for a single milestone.
 * The milestone's own status is included for context but does not affect
 * the calculation.
 */
public interface GetMilestoneProgressUseCase {

    /**
     * Retrieves progress for a milestone.
     *
     * @param workspaceId workspace scope for tenant isolation
     * @param projectId   project scope for tenant isolation
     * @param milestoneId milestone to query
     * @param userId      the authenticated user (for access check)
     * @param requestId   request identifier for tracing
     * @return milestone progress result
     */
    MilestoneProgressResult getMilestoneProgress(UUID workspaceId, UUID projectId,
                                                  UUID milestoneId,
                                                  UUID userId, String requestId);
}
