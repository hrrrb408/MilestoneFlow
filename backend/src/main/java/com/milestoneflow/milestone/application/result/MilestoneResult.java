package com.milestoneflow.milestone.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Result record for a single milestone operation.
 *
 * <p>Carries milestone data without internal auditing fields (createdBy, updatedBy).
 * Uses managed-entity data to ensure createdAt is populated.
 *
 * @param milestoneId milestone unique identifier
 * @param workspaceId owning workspace identifier
 * @param projectId   parent project identifier
 * @param title       milestone title
 * @param description milestone description (nullable)
 * @param status      milestone status
 * @param dueDate     target completion date (nullable)
 * @param completedAt completion timestamp (nullable, set when COMPLETED)
 * @param createdAt   creation timestamp
 * @param updatedAt   last update timestamp
 */
public record MilestoneResult(
        UUID milestoneId,
        UUID workspaceId,
        UUID projectId,
        String title,
        String description,
        String status,
        LocalDate dueDate,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
