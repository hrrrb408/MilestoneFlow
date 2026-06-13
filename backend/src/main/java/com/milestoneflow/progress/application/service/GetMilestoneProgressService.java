package com.milestoneflow.progress.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.progress.application.port.in.GetMilestoneProgressUseCase;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.TaskCountProjection;
import com.milestoneflow.progress.application.result.MilestoneProgressResult;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for querying milestone-level progress.
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Verify project belongs to workspace (404 if not)</li>
 *   <li>Verify milestone belongs to project (404 if not)</li>
 *   <li>Aggregate task counts for the milestone</li>
 *   <li>Calculate completion rate from task aggregation</li>
 * </ol>
 *
 * <p>ARCHIVED projects and COMPLETED milestones are readable — progress is a read-only query.
 */
@Service
public class GetMilestoneProgressService implements GetMilestoneProgressUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetMilestoneProgressService.class);

    private final ProgressQueryRepository progressQueryRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public GetMilestoneProgressService(ProgressQueryRepository progressQueryRepository,
                                       ProjectRepository projectRepository,
                                       MilestoneRepository milestoneRepository,
                                       WorkspaceAccessChecker workspaceAccessChecker) {
        this.progressQueryRepository = progressQueryRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneProgressResult getMilestoneProgress(UUID workspaceId, UUID projectId,
                                                         UUID milestoneId,
                                                         UUID userId, String requestId) {
        // 1. Workspace membership check
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace (no status check — ARCHIVED is readable)
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify milestone belongs to project (no status check — COMPLETED is readable)
        Milestone milestone = milestoneRepository
                .findByWorkspaceIdAndProjectIdAndId(workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        // 4. Aggregate task counts for this milestone
        TaskCountProjection counts = progressQueryRepository.countTasksByMilestone(
                workspaceId, projectId, milestoneId);

        long totalTasks = counts.totalTasks();
        long completedTasks = counts.completedTasks();
        long openTasks = totalTasks - completedTasks;

        log.debug("Milestone progress: milestoneId={}, totalTasks={}, completedTasks={}, openTasks={}",
                milestoneId, totalTasks, completedTasks, openTasks);

        return new MilestoneProgressResult(
                workspaceId,
                projectId,
                milestoneId,
                milestone.getTitle(),
                milestone.getStatus().name(),
                totalTasks,
                completedTasks,
                openTasks,
                ProgressRateCalculator.calculate(completedTasks, totalTasks)
        );
    }
}
