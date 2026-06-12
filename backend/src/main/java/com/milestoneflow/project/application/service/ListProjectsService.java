package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.port.in.ListProjectsUseCase;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for listing projects within a workspace.
 *
 * <p>Returns only ACTIVE projects by default. The caller must be an ACTIVE member
 * of the workspace.
 */
@Service
public class ListProjectsService implements ListProjectsUseCase {

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListProjectsService(ProjectRepository projectRepository,
                               WorkspaceAccessChecker workspaceAccessChecker) {
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResult> listActiveProjects(UUID workspaceId, UUID userId, String requestId) {
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        return projectRepository.findActiveByWorkspaceId(workspaceId)
                .stream()
                .map(ListProjectsService::toResult)
                .toList();
    }

    private static ProjectResult toResult(Project project) {
        return new ProjectResult(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getStartDate(),
                project.getTargetDate(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
