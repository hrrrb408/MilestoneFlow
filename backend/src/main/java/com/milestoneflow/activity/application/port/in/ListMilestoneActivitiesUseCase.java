package com.milestoneflow.activity.application.port.in;

import com.milestoneflow.activity.application.result.ActivityLogRow;

import java.util.List;
import java.util.UUID;

/**
 * Use case for listing activity events for a specific milestone.
 *
 * <p>Returns only events with {@code targetType = 'MILESTONE'} and
 * {@code targetId = milestoneId}. Child-entity events (tasks) are not
 * included because the audit metadata does not carry milestoneId.
 * Results are ordered by {@code createdAt DESC}.
 */
public interface ListMilestoneActivitiesUseCase {

    List<ActivityLogRow> listMilestoneActivities(UUID workspaceId, UUID projectId,
                                                  UUID milestoneId, UUID userId,
                                                  int limit, String requestId);
}
