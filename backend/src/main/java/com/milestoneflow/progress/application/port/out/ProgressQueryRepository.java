package com.milestoneflow.progress.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Output port for progress-related read queries.
 *
 * <p>Provides count-based projections for task and milestone aggregation.
 * All methods require workspace_id (and project_id) for tenant isolation.
 * Implementations should use native SQL with PostgreSQL conditional aggregates
 * rather than loading entities.
 */
public interface ProgressQueryRepository {

    /**
     * Counts tasks by status for a given project.
     *
     * @param workspaceId workspace scope
     * @param projectId   project scope
     * @return task count projection
     */
    TaskCountProjection countTasksByProject(UUID workspaceId, UUID projectId);

    /**
     * Counts tasks by status for a given milestone.
     *
     * @param workspaceId workspace scope
     * @param projectId   project scope
     * @param milestoneId milestone scope
     * @return task count projection
     */
    TaskCountProjection countTasksByMilestone(UUID workspaceId, UUID projectId, UUID milestoneId);

    /**
     * Counts milestones by status for a given project.
     *
     * @param workspaceId workspace scope
     * @param projectId   project scope
     * @return milestone count projection
     */
    MilestoneCountProjection countMilestonesByProject(UUID workspaceId, UUID projectId);

    /**
     * Returns per-milestone task counts for all milestones in a project.
     *
     * <p>Includes milestones with zero tasks (LEFT JOIN).
     * Ordered by due date ASC (nulls last), then created_at ASC.
     *
     * @param workspaceId workspace scope
     * @param projectId   project scope
     * @return list of per-milestone projections
     */
    List<MilestoneTaskCountProjection> countTasksPerMilestone(UUID workspaceId, UUID projectId);

    // ── Projection records ─────────────────────────────────────────────

    /**
     * Task count projection for completion rate calculation.
     *
     * @param totalTasks     total tasks (OPEN + COMPLETED)
     * @param completedTasks tasks with status COMPLETED
     */
    record TaskCountProjection(long totalTasks, long completedTasks) {
    }

    /**
     * Milestone count projection for project-level milestone stats.
     *
     * @param totalMilestones    total milestones
     * @param completedMilestones milestones with status COMPLETED
     * @param openMilestones     milestones with status OPEN
     */
    record MilestoneCountProjection(long totalMilestones, long completedMilestones, long openMilestones) {
    }

    /**
     * Per-milestone task count with milestone metadata.
     *
     * @param milestoneId    milestone identifier
     * @param title          milestone title
     * @param totalTasks     total tasks in this milestone
     * @param completedTasks completed tasks in this milestone
     * @param status         milestone status (OPEN / COMPLETED)
     */
    record MilestoneTaskCountProjection(
            UUID milestoneId,
            String title,
            long totalTasks,
            long completedTasks,
            String status
    ) {
    }
}
