package com.milestoneflow.milestone.application.service;

import com.milestoneflow.milestone.application.port.in.GetMilestoneUseCase;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.application.result.MilestoneResult;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for retrieving a single milestone's details.
 *
 * <p>Uses composite key lookup (workspaceId + projectId + milestoneId)
 * to prevent cross-project and cross-workspace data leakage.
 * ARCHIVED projects can still be queried for milestones (read-only).
 */
@Service
public class GetMilestoneService implements GetMilestoneUseCase {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public GetMilestoneService(MilestoneRepository milestoneRepository,
                               ProjectRepository projectRepository,
                               WorkspaceAccessChecker workspaceAccessChecker) {
        this.milestoneRepository = milestoneRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneResult getMilestone(UUID workspaceId, UUID projectId,
                                        UUID milestoneId, UUID userId, String requestId) {
        // 1. Verify workspace membership
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Load milestone by composite key (prevents cross-project/workspace leakage)
        Milestone milestone = milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                        workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        return toResult(milestone);
    }

    private static MilestoneResult toResult(Milestone milestone) {
        return new MilestoneResult(
                milestone.getId(),
                milestone.getWorkspaceId(),
                milestone.getProjectId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getStatus().name(),
                milestone.getDueDate(),
                milestone.getCreatedAt(),
                milestone.getUpdatedAt()
        );
    }
}
