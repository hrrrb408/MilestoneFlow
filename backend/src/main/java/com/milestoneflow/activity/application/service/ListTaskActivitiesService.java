package com.milestoneflow.activity.application.service;

import com.milestoneflow.activity.application.port.in.ListTaskActivitiesUseCase;
import com.milestoneflow.activity.application.port.out.ActivityLogQueryRepository;
import com.milestoneflow.activity.application.result.ActivityLogRow;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for listing activity events for a specific task.
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Verify project belongs to workspace (404 if not)</li>
 *   <li>Verify milestone belongs to project/workspace (404 if not)</li>
 *   <li>Verify task belongs to milestone/project/workspace (404 if not)</li>
 *   <li>Query audit_event for task-level events only</li>
 * </ol>
 *
 * <p>COMPLETED tasks are readable — activity log is a read-only query.
 */
@Service
public class ListTaskActivitiesService implements ListTaskActivitiesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListTaskActivitiesService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final ActivityLogQueryRepository activityLogQueryRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListTaskActivitiesService(ActivityLogQueryRepository activityLogQueryRepository,
                                      ProjectRepository projectRepository,
                                      MilestoneRepository milestoneRepository,
                                      TaskRepository taskRepository,
                                      WorkspaceAccessChecker workspaceAccessChecker) {
        this.activityLogQueryRepository = activityLogQueryRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.taskRepository = taskRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogRow> listTaskActivities(UUID workspaceId, UUID projectId,
                                                    UUID milestoneId, UUID taskId,
                                                    UUID userId, int limit,
                                                    String requestId) {
        // 1. Workspace membership check
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify milestone belongs to project/workspace
        milestoneRepository.findByWorkspaceIdAndProjectIdAndId(workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        // 4. Verify task belongs to milestone/project/workspace
        taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                        workspaceId, projectId, milestoneId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        // 5. Clamp limit
        int effectiveLimit = clampLimit(limit);

        log.debug("Listing task activities: workspaceId={}, projectId={}, milestoneId={}, taskId={}, limit={}",
                workspaceId, projectId, milestoneId, taskId, effectiveLimit);

        // 6. Query
        return activityLogQueryRepository.findByTask(workspaceId, projectId,
                milestoneId, taskId, effectiveLimit);
    }

    static int clampLimit(int limit) {
        if (limit < MIN_LIMIT) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
