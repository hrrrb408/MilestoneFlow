package com.milestoneflow.project.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command for updating basic project information.
 *
 * <p>Only non-null fields will be applied.
 *
 * @param workspaceId the workspace scope for access validation
 * @param projectId   the project to update
 * @param name        new name (nullable to skip)
 * @param description new description (nullable to skip)
 * @param startDate   new start date (nullable to skip)
 * @param targetDate  new target date (nullable to skip)
 */
public record UpdateProjectCommand(
        UUID workspaceId,
        UUID projectId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate targetDate
) {
}
