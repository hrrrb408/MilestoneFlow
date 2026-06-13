package com.milestoneflow.activity.application.service;

import com.milestoneflow.activity.application.port.in.ListProjectActivitiesUseCase;
import com.milestoneflow.activity.application.port.out.ActivityLogQueryRepository;
import com.milestoneflow.activity.application.result.ActivityLogRow;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for listing activity events for a specific project.
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Verify project belongs to workspace (404 if not)</li>
 *   <li>Query audit_event for project-level events only</li>
 * </ol>
 *
 * <p>ARCHIVED projects are readable — activity log is a read-only query.
 */
@Service
public class ListProjectActivitiesService implements ListProjectActivitiesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListProjectActivitiesService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final ActivityLogQueryRepository activityLogQueryRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListProjectActivitiesService(ActivityLogQueryRepository activityLogQueryRepository,
                                         ProjectRepository projectRepository,
                                         WorkspaceAccessChecker workspaceAccessChecker) {
        this.activityLogQueryRepository = activityLogQueryRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogRow> listProjectActivities(UUID workspaceId, UUID projectId,
                                                       UUID userId, int limit,
                                                       String requestId) {
        // 1. Workspace membership check
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace (no status check — ARCHIVED is readable)
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Clamp limit
        int effectiveLimit = clampLimit(limit);

        log.debug("Listing project activities: workspaceId={}, projectId={}, limit={}",
                workspaceId, projectId, effectiveLimit);

        // 4. Query
        return activityLogQueryRepository.findByProject(workspaceId, projectId, effectiveLimit);
    }

    static int clampLimit(int limit) {
        if (limit < MIN_LIMIT) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
