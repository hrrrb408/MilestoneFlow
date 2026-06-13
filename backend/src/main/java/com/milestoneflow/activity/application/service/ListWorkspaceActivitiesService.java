package com.milestoneflow.activity.application.service;

import com.milestoneflow.activity.application.port.in.ListWorkspaceActivitiesUseCase;
import com.milestoneflow.activity.application.port.out.ActivityLogQueryRepository;
import com.milestoneflow.activity.application.result.ActivityLogRow;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for listing activity events within a workspace scope.
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Query audit_event for all events in the workspace</li>
 *   <li>Return results ordered by createdAt DESC</li>
 * </ol>
 *
 * <p>This is a read-only service — it never writes audit events.
 */
@Service
public class ListWorkspaceActivitiesService implements ListWorkspaceActivitiesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListWorkspaceActivitiesService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final ActivityLogQueryRepository activityLogQueryRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListWorkspaceActivitiesService(ActivityLogQueryRepository activityLogQueryRepository,
                                           WorkspaceAccessChecker workspaceAccessChecker) {
        this.activityLogQueryRepository = activityLogQueryRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogRow> listWorkspaceActivities(UUID workspaceId, UUID userId,
                                                         int limit,
                                                         String eventType, String targetType,
                                                         String requestId) {
        // 1. Workspace membership check
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Clamp limit
        int effectiveLimit = clampLimit(limit);

        log.debug("Listing workspace activities: workspaceId={}, limit={}, eventType={}, targetType={}",
                workspaceId, effectiveLimit, eventType, targetType);

        // 3. Query
        return activityLogQueryRepository.findByWorkspace(workspaceId, effectiveLimit,
                eventType, targetType);
    }

    static int clampLimit(int limit) {
        if (limit < MIN_LIMIT) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
