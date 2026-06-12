package com.milestoneflow.milestone.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command for creating a new milestone within a project.
 *
 * @param workspaceId the owning workspace
 * @param projectId   the parent project
 * @param title       milestone title (required)
 * @param description milestone description (nullable)
 * @param dueDate     target completion date (nullable)
 */
public record CreateMilestoneCommand(
        UUID workspaceId,
        UUID projectId,
        String title,
        String description,
        LocalDate dueDate
) {
}
