package com.milestoneflow.milestone.infrastructure.persistence;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.milestone.domain.type.MilestoneStatus;
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
public class MilestoneRepositoryAdapter implements MilestoneRepository {

    private final SpringDataMilestoneRepository delegate;

    MilestoneRepositoryAdapter(SpringDataMilestoneRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Milestone save(Milestone milestone) {
        return delegate.saveAndFlush(milestone);
    }

    @Override
    public Optional<Milestone> findByWorkspaceIdAndProjectIdAndId(UUID workspaceId, UUID projectId, UUID milestoneId) {
        return delegate.findByWorkspaceIdAndProjectIdAndId(workspaceId, projectId, milestoneId);
    }

    @Override
    public List<Milestone> findByWorkspaceIdAndProjectId(UUID workspaceId, UUID projectId) {
        return delegate.findAllByWorkspaceIdAndProjectIdOrdered(workspaceId, projectId);
    }

    @Override
    public List<Milestone> findByWorkspaceIdAndProjectIdAndStatus(UUID workspaceId, UUID projectId, MilestoneStatus status) {
        return delegate.findByWorkspaceIdAndProjectIdAndStatusOrdered(workspaceId, projectId, status);
    }
}
