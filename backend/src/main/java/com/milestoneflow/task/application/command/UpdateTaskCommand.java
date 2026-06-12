package com.milestoneflow.task.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTaskCommand(
        UUID workspaceId,
        UUID projectId,
        UUID milestoneId,
        UUID taskId,
        String title,
        String description,
        String priority,
        LocalDate dueDate
) {}
