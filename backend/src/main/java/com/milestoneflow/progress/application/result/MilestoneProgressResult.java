package com.milestoneflow.progress.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application-layer result for milestone progress queries.
 *
 * <p>Carries aggregated task counts and the computed completion rate for
 * a single milestone. The milestone's own status is included for context
 * but does not affect the calculation — only task statuses are counted.
 *
 * @param workspaceId    workspace scope
 * @param projectId      project scope
 * @param milestoneId    milestone identifier
 * @param milestoneTitle milestone title for display
 * @param milestoneStatus milestone status (OPEN / COMPLETED)
 * @param totalTasks     total tasks in the milestone
 * @param completedTasks tasks with status COMPLETED
 * @param openTasks      tasks with status OPEN
 * @param completionRate percentage (0.00–100.00), 0.00 when no tasks exist
 */
public record MilestoneProgressResult(
        UUID workspaceId,
        UUID projectId,
        UUID milestoneId,
        String milestoneTitle,
        String milestoneStatus,
        long totalTasks,
        long completedTasks,
        long openTasks,
        BigDecimal completionRate
) {
}
