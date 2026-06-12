package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.port.in.GetProjectUseCase;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for retrieving a single project.
 *
 * <p>Uses composite key lookup (workspaceId + projectId) to prevent
 * cross-workspace information leakage.
 */
@Service
public class GetProjectService implements GetProjectUseCase {

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public GetProjectService(ProjectRepository projectRepository,
                             WorkspaceAccessChecker workspaceAccessChecker) {
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResult getProject(UUID workspaceId, UUID projectId, UUID userId, String requestId) {
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        Project project = projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        return toResult(project);
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
