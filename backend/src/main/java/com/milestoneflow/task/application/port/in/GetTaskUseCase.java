package com.milestoneflow.task.application.port.in;

import com.milestoneflow.task.application.result.TaskResult;

import java.util.UUID;

public interface GetTaskUseCase {
    TaskResult getTask(UUID workspaceId, UUID projectId, UUID milestoneId,
                       UUID taskId, UUID userId, String requestId);
}
