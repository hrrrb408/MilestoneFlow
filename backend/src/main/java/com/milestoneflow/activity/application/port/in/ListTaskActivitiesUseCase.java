package com.milestoneflow.activity.application.port.in;

import com.milestoneflow.activity.application.result.ActivityLogRow;

import java.util.List;
import java.util.UUID;

/**
 * Use case for listing activity events for a specific task.
 *
 * <p>Returns events with {@code targetType = 'TASK'} and
 * {@code targetId = taskId}. Results are ordered by {@code createdAt DESC}.
 */
public interface ListTaskActivitiesUseCase {

    List<ActivityLogRow> listTaskActivities(UUID workspaceId, UUID projectId,
                                             UUID milestoneId, UUID taskId,
                                             UUID userId, int limit,
                                             String requestId);
}
