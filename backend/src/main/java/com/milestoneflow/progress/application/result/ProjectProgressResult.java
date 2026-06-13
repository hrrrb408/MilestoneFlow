package com.milestoneflow.progress.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application-layer result for project progress queries.
 *
 * <p>Carries aggregated task counts, milestone counts, and the computed
 * completion rate for a single project. The completion rate is based on
 * task-level aggregation (not milestone average) per the B6-001 spec.
 *
 * @param workspaceId        workspace scope
 * @param projectId          project identifier
 * @param totalTasks         total tasks in the project (OPEN + COMPLETED)
 * @param completedTasks     tasks with status COMPLETED
 * @param openTasks          tasks with status OPEN
 * @param completionRate     percentage (0.00–100.00), 0.00 when no tasks exist
 * @param totalMilestones    total milestones in the project
 * @param completedMilestones milestones with status COMPLETED
 * @param openMilestones     milestones with status OPEN
 */
public record ProjectProgressResult(
        UUID workspaceId,
        UUID projectId,
        long totalTasks,
        long completedTasks,
        long openTasks,
        BigDecimal completionRate,
        long totalMilestones,
        long completedMilestones,
        long openMilestones
) {
}
