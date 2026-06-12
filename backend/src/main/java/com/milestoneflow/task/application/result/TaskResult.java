package com.milestoneflow.task.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResult(
        UUID taskId,
        UUID workspaceId,
        UUID projectId,
        UUID milestoneId,
        String title,
        String description,
        String status,
        String priority,
        LocalDate dueDate,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}
