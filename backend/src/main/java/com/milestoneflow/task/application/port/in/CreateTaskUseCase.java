package com.milestoneflow.task.application.port.in;

import com.milestoneflow.task.application.command.CreateTaskCommand;
import com.milestoneflow.task.application.result.TaskResult;

import java.util.UUID;

public interface CreateTaskUseCase {
    TaskResult create(CreateTaskCommand command, UUID userId, String requestId);
}
