package com.milestoneflow.activity.application.port.in;

import com.milestoneflow.activity.application.result.ActivityLogRow;

import java.util.List;
import java.util.UUID;

/**
 * Use case for listing activity events within a workspace scope.
 *
 * <p>Returns all audit events for the given workspace, optionally filtered
 * by event type or target type. Results are ordered by {@code createdAt DESC}.
 */
public interface ListWorkspaceActivitiesUseCase {

    List<ActivityLogRow> listWorkspaceActivities(UUID workspaceId, UUID userId,
                                                  int limit,
                                                  String eventType, String targetType,
                                                  String requestId);
}
