package com.milestoneflow.project.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Result record for a single project operation.
 *
 * <p>Carries project data without internal auditing fields.
 * Uses managed-entity data to ensure createdAt is populated.
 *
 * @param projectId   project unique identifier
 * @param workspaceId owning workspace identifier
 * @param name        project display name
 * @param description project description (nullable)
 * @param status      project status
 * @param startDate   planned start date (nullable)
 * @param targetDate  target completion date (nullable)
 * @param createdAt   creation timestamp
 * @param updatedAt   last update timestamp
 */
public record ProjectResult(
        UUID projectId,
        UUID workspaceId,
        String name,
        String description,
        String status,
        LocalDate startDate,
        LocalDate targetDate,
        Instant createdAt,
        Instant updatedAt
) {
}
