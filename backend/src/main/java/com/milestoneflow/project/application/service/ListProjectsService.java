package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.port.in.ListProjectsUseCase;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.project.domain.type.ProjectStatus;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application service for listing projects within a workspace.
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>{@code status} parameter takes priority over {@code includeArchived}.</li>
 *   <li>Default (no params): returns only ACTIVE projects.</li>
 *   <li>{@code includeArchived=true}: returns ACTIVE + ARCHIVED.</li>
 *   <li>{@code status=ACTIVE}: returns only ACTIVE.</li>
 *   <li>{@code status=ARCHIVED}: returns only ARCHIVED.</li>
 * </ul>
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
        return listProjects(workspaceId, userId, requestId, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResult> listProjects(UUID workspaceId, UUID userId, String requestId,
                                            Boolean includeArchived, String status) {
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        List<ProjectStatus> statuses = resolveStatuses(includeArchived, status);

        List<Project> projects;
        if (statuses.size() == 1) {
            projects = projectRepository.findActiveByWorkspaceId(workspaceId);
        } else {
            projects = projectRepository.findByWorkspaceIdAndStatuses(workspaceId, statuses);
        }

        return projects.stream()
                .map(ListProjectsService::toResult)
                .toList();
    }

    /**
     * Resolves the effective status list from the request parameters.
     *
     * <p>{@code status} takes priority over {@code includeArchived}.
     */
    private List<ProjectStatus> resolveStatuses(Boolean includeArchived, String status) {
        if (status != null && !status.isBlank()) {
            return List.of(ProjectStatus.valueOf(status));
        }

        boolean include = includeArchived != null && includeArchived;
        if (include) {
            List<ProjectStatus> all = new ArrayList<>();
            all.add(ProjectStatus.ACTIVE);
            all.add(ProjectStatus.ARCHIVED);
            return all;
        }

        return List.of(ProjectStatus.ACTIVE);
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
                project.getArchivedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
