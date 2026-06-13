package com.milestoneflow.activity.application.port.in;

import com.milestoneflow.activity.application.result.ActivityLogRow;

import java.util.List;
import java.util.UUID;

/**
 * Use case for listing activity events for a specific project.
 *
 * <p>Returns only events with {@code targetType = 'PROJECT'} and
 * {@code targetId = projectId}. Child-entity events (milestones, tasks)
 * are not included because the audit metadata does not carry projectId.
 * Results are ordered by {@code createdAt DESC}.
 */
public interface ListProjectActivitiesUseCase {

    List<ActivityLogRow> listProjectActivities(UUID workspaceId, UUID projectId,
                                                UUID userId, int limit,
                                                String requestId);
}
