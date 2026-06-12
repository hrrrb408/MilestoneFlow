package com.milestoneflow.task.application.port.in;

import com.milestoneflow.task.application.result.TaskResult;

import java.util.List;
import java.util.UUID;

public interface ListTasksUseCase {
    List<TaskResult> listTasks(UUID workspaceId, UUID projectId, UUID milestoneId,
                               UUID userId, String requestId,
                               String status, String priority);
}
