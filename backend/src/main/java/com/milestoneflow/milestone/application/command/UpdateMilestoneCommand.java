package com.milestoneflow.milestone.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command for updating basic milestone information.
 *
 * @param workspaceId the owning workspace
 * @param projectId   the parent project
 * @param milestoneId the milestone to update
 * @param title       new title (nullable to skip)
 * @param description new description (nullable to skip)
 * @param dueDate     new due date (nullable to skip)
 */
public record UpdateMilestoneCommand(
        UUID workspaceId,
        UUID projectId,
        UUID milestoneId,
        String title,
        String description,
        LocalDate dueDate
) {
}
