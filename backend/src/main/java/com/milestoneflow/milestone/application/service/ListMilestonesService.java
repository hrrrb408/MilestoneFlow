package com.milestoneflow.milestone.application.service;

import com.milestoneflow.milestone.application.port.in.ListMilestonesUseCase;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.application.result.MilestoneResult;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.milestone.domain.type.MilestoneStatus;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for listing milestones within a project.
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>Default (no status param): returns all milestones ordered by due date ASC NULLS LAST.</li>
 *   <li>{@code status=OPEN}: returns only OPEN milestones.</li>
 *   <li>{@code status=COMPLETED}: returns only COMPLETED milestones.</li>
 * </ul>
 *
 * <p>ARCHIVED projects can still be queried for milestones (read-only access).
 */
@Service
public class ListMilestonesService implements ListMilestonesUseCase {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListMilestonesService(MilestoneRepository milestoneRepository,
                                 ProjectRepository projectRepository,
                                 WorkspaceAccessChecker workspaceAccessChecker) {
        this.milestoneRepository = milestoneRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResult> listMilestones(UUID workspaceId, UUID projectId,
                                                UUID userId, String requestId,
                                                String status) {
        // 1. Verify workspace membership
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Query milestones with optional status filter
        List<Milestone> milestones;
        if (status != null && !status.isBlank()) {
            MilestoneStatus statusFilter = MilestoneStatus.valueOf(status);
            milestones = milestoneRepository.findByWorkspaceIdAndProjectIdAndStatus(
                    workspaceId, projectId, statusFilter);
        } else {
            milestones = milestoneRepository.findByWorkspaceIdAndProjectId(workspaceId, projectId);
        }

        return milestones.stream()
                .map(ListMilestonesService::toResult)
                .toList();
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
