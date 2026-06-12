package com.milestoneflow.task.application.port.in;

import com.milestoneflow.task.application.result.TaskResult;

import java.util.UUID;

/**
 * Use case for reopening a completed task.
 *
 * <p>Requires OWNER role. The task must be in COMPLETED status.
 * After reopening, the task status becomes OPEN with
 * {@code completedAt} and {@code completedBy} cleared.
 */
public interface ReopenTaskUseCase {

    /**
     * Reopens the specified task.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param milestoneId the milestone scope
     * @param taskId      the task to reopen
     * @param userId      the authenticated user performing the action
     * @param requestId   the request correlation ID (nullable)
     * @return the reopened task result
     */
    TaskResult reopen(UUID workspaceId, UUID projectId, UUID milestoneId,
                      UUID taskId, UUID userId, String requestId);
}
