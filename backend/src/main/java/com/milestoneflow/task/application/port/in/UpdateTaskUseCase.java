package com.milestoneflow.task.application.port.in;

import com.milestoneflow.task.application.command.UpdateTaskCommand;
import com.milestoneflow.task.application.result.TaskResult;

import java.util.UUID;

public interface UpdateTaskUseCase {
    TaskResult update(UpdateTaskCommand command, UUID userId, String requestId);
}
