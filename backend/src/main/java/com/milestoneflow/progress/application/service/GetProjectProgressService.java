package com.milestoneflow.progress.application.service;

import com.milestoneflow.progress.application.port.in.GetProjectProgressUseCase;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.MilestoneCountProjection;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.TaskCountProjection;
import com.milestoneflow.progress.application.result.ProjectProgressResult;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for querying project-level progress.
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Verify project belongs to workspace (404 if not)</li>
 *   <li>Aggregate task counts for the entire project</li>
 *   <li>Aggregate milestone counts for the project</li>
 *   <li>Calculate completion rate from task aggregation</li>
 * </ol>
 *
 * <p>ARCHIVED projects are readable — progress is a read-only query.
 */
@Service
public class GetProjectProgressService implements GetProjectProgressUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetProjectProgressService.class);

    private final ProgressQueryRepository progressQueryRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public GetProjectProgressService(ProgressQueryRepository progressQueryRepository,
                                     ProjectRepository projectRepository,
                                     WorkspaceAccessChecker workspaceAccessChecker) {
        this.progressQueryRepository = progressQueryRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectProgressResult getProjectProgress(UUID workspaceId, UUID projectId,
                                                     UUID userId, String requestId) {
        // 1. Workspace membership check
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace (no status check — ARCHIVED is readable)
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Aggregate task counts for the entire project
        TaskCountProjection taskCounts = progressQueryRepository.countTasksByProject(workspaceId, projectId);

        // 4. Aggregate milestone counts
        MilestoneCountProjection milestoneCounts = progressQueryRepository
                .countMilestonesByProject(workspaceId, projectId);

        long totalTasks = taskCounts.totalTasks();
        long completedTasks = taskCounts.completedTasks();
        long openTasks = totalTasks - completedTasks;

        log.debug("Project progress: projectId={}, totalTasks={}, completedTasks={}, openTasks={}",
                projectId, totalTasks, completedTasks, openTasks);

        return new ProjectProgressResult(
                workspaceId,
                projectId,
                totalTasks,
                completedTasks,
                openTasks,
                ProgressRateCalculator.calculate(completedTasks, totalTasks),
                milestoneCounts.totalMilestones(),
                milestoneCounts.completedMilestones(),
                milestoneCounts.openMilestones()
        );
    }
}
