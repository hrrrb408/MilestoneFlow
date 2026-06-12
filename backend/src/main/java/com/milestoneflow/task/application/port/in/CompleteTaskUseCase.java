package com.milestoneflow.task.application.port.in;

import com.milestoneflow.task.application.result.TaskResult;

import java.util.UUID;

/**
 * Use case for completing a task.
 *
 * <p>Requires OWNER role. The task must be in OPEN status.
 * After completion, the task status becomes COMPLETED with
 * {@code completedAt} and {@code completedBy} populated.
 */
public interface CompleteTaskUseCase {

    /**
     * Completes the specified task.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param milestoneId the milestone scope
     * @param taskId      the task to complete
     * @param userId      the authenticated user performing the action
     * @param requestId   the request correlation ID (nullable)
     * @return the completed task result
     */
    TaskResult complete(UUID workspaceId, UUID projectId, UUID milestoneId,
                        UUID taskId, UUID userId, String requestId);
}
