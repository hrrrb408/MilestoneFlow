package com.milestoneflow.progress.application.service;

import com.milestoneflow.progress.application.port.in.ListMilestoneProgressUseCase;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.MilestoneTaskCountProjection;
import com.milestoneflow.progress.application.result.MilestoneProgressResult;
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
 * Application service for listing progress of all milestones in a project.
 *
 * <p>Flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Verify project belongs to workspace (404 if not)</li>
 *   <li>Query per-milestone task counts via LEFT JOIN (includes zero-task milestones)</li>
 *   <li>Calculate completion rate for each milestone independently</li>
 * </ol>
 *
 * <p>ARCHIVED projects are readable — progress is a read-only query.
 * Ordered by due date ASC (nulls last), then created_at ASC.
 */
@Service
public class ListMilestoneProgressService implements ListMilestoneProgressUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListMilestoneProgressService.class);

    private final ProgressQueryRepository progressQueryRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListMilestoneProgressService(ProgressQueryRepository progressQueryRepository,
                                        ProjectRepository projectRepository,
                                        WorkspaceAccessChecker workspaceAccessChecker) {
        this.progressQueryRepository = progressQueryRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneProgressResult> listMilestoneProgress(UUID workspaceId,
                                                                UUID projectId,
                                                                UUID userId,
                                                                String requestId) {
        // 1. Workspace membership check
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace (no status check — ARCHIVED is readable)
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Query per-milestone task counts (single SQL with LEFT JOIN)
        List<MilestoneTaskCountProjection> rows =
                progressQueryRepository.countTasksPerMilestone(workspaceId, projectId);

        log.debug("Milestone progress list: projectId={}, milestoneCount={}", projectId, rows.size());

        // 4. Map to results with calculated completion rates
        return rows.stream()
                .map(row -> {
                    long totalTasks = row.totalTasks();
                    long completedTasks = row.completedTasks();
                    long openTasks = totalTasks - completedTasks;
                    return new MilestoneProgressResult(
                            workspaceId,
                            projectId,
                            row.milestoneId(),
                            row.title(),
                            row.status(),
                            totalTasks,
                            completedTasks,
                            openTasks,
                            ProgressRateCalculator.calculate(completedTasks, totalTasks)
                    );
                })
                .toList();
    }
}
