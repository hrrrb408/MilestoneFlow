package com.milestoneflow.project.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command for creating a new project within a workspace.
 *
 * @param workspaceId the workspace that will own the project
 * @param name        project display name (required)
 * @param description project description (nullable)
 * @param startDate   planned start date (nullable)
 * @param targetDate  target completion date (nullable)
 */
public record CreateProjectCommand(
        UUID workspaceId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate targetDate
) {
}
