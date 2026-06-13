package com.milestoneflow.progress.application.port.in;

import com.milestoneflow.progress.application.result.MilestoneProgressResult;

import java.util.List;
import java.util.UUID;

/**
 * Use case for listing progress of all milestones in a project.
 *
 * <p>Returns a list of milestone progress entries, each with independent
 * task counts and completion rates. Ordered by due date (nulls last),
 * then creation time ascending.
 */
public interface ListMilestoneProgressUseCase {

    /**
     * Lists progress for all milestones in a project.
     *
     * @param workspaceId workspace scope for tenant isolation
     * @param projectId   project to query
     * @param userId      the authenticated user (for access check)
     * @param requestId   request identifier for tracing
     * @return list of milestone progress results
     */
    List<MilestoneProgressResult> listMilestoneProgress(UUID workspaceId, UUID projectId,
                                                         UUID userId, String requestId);
}
