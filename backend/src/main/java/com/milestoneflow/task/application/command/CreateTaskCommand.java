package com.milestoneflow.task.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskCommand(
        UUID workspaceId,
        UUID projectId,
        UUID milestoneId,
        String title,
        String description,
        String priority,
        LocalDate dueDate
) {}
