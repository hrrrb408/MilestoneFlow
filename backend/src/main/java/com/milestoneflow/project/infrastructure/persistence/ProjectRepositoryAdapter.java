package com.milestoneflow.project.infrastructure.persistence;

import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.project.domain.type.ProjectStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 */
@Component
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final SpringDataProjectRepository delegate;

    ProjectRepositoryAdapter(SpringDataProjectRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Project save(Project project) {
        return delegate.saveAndFlush(project);
    }

    @Override
    public Optional<Project> findByWorkspaceIdAndId(UUID workspaceId, UUID projectId) {
        return delegate.findByWorkspaceIdAndId(workspaceId, projectId);
    }

    @Override
    public List<Project> findActiveByWorkspaceId(UUID workspaceId) {
        return delegate.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, ProjectStatus.ACTIVE);
    }

    @Override
    public List<Project> findByWorkspaceId(UUID workspaceId) {
        return delegate.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }
}
