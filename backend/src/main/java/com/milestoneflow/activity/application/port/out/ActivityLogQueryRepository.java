package com.milestoneflow.activity.application.port.out;

import com.milestoneflow.activity.application.result.ActivityLogRow;

import java.util.List;
import java.util.UUID;

/**
 * Output port for querying activity log entries from the audit_event table.
 *
 * <p>Each method enforces workspace-scoped data isolation. Project, milestone,
 * and task scope queries use {@code target_type} / {@code target_id} matching
 * — this is the safe minimum scope that avoids unsafe cross-table inference.
 *
 * <p>All queries return results ordered by {@code created_at DESC, id DESC}
 * with a configurable {@code limit}.
 */
public interface ActivityLogQueryRepository {

    /**
     * Lists all activity events for a workspace, optionally filtered.
     *
     * @param workspaceId workspace scope (required)
     * @param limit       maximum number of results (1–100)
     * @param eventType   optional filter on the action column (e.g., "TASK_CREATED")
     * @param targetType  optional filter on the target_type column (e.g., "TASK")
     * @return activity rows ordered by created_at DESC, never null
     */
    List<ActivityLogRow> findByWorkspace(UUID workspaceId, int limit,
                                          String eventType, String targetType);

    /**
     * Lists activity events for a specific project (target_type = 'PROJECT').
     *
     * @param workspaceId workspace scope (required)
     * @param projectId   project identifier
     * @param limit       maximum number of results (1–100)
     * @return activity rows ordered by created_at DESC, never null
     */
    List<ActivityLogRow> findByProject(UUID workspaceId, UUID projectId, int limit);

    /**
     * Lists activity events for a specific milestone (target_type = 'MILESTONE').
     *
     * @param workspaceId workspace scope (required)
     * @param projectId   project identifier (for composite key matching)
     * @param milestoneId milestone identifier
     * @param limit       maximum number of results (1–100)
     * @return activity rows ordered by created_at DESC, never null
     */
    List<ActivityLogRow> findByMilestone(UUID workspaceId, UUID projectId,
                                          UUID milestoneId, int limit);

    /**
     * Lists activity events for a specific task (target_type = 'TASK').
     *
     * @param workspaceId workspace scope (required)
     * @param projectId   project identifier (for composite key matching)
     * @param milestoneId milestone identifier (for composite key matching)
     * @param taskId      task identifier
     * @param limit       maximum number of results (1–100)
     * @return activity rows ordered by created_at DESC, never null
     */
    List<ActivityLogRow> findByTask(UUID workspaceId, UUID projectId,
                                     UUID milestoneId, UUID taskId, int limit);
}
