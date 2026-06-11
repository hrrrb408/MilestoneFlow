package com.milestoneflow.workspace.infrastructure.persistence;

import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 */
@Component
public class WorkspaceMembershipRepositoryAdapter implements WorkspaceMembershipRepository {

    private final SpringDataWorkspaceMembershipRepository delegate;

    WorkspaceMembershipRepositoryAdapter(SpringDataWorkspaceMembershipRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkspaceMembership save(WorkspaceMembership membership) {
        return delegate.saveAndFlush(membership);
    }

    @Override
    public Optional<WorkspaceMembership> findActiveByWorkspaceIdAndUserId(UUID workspaceId, UUID userId) {
        return delegate.findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, WorkspaceMembershipStatus.ACTIVE);
    }

    @Override
    public Optional<WorkspaceMembership> findActiveByUserId(UUID userId) {
        return delegate.findByUserIdAndStatus(userId, WorkspaceMembershipStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByWorkspaceIdAndUserId(UUID workspaceId, UUID userId) {
        return delegate.existsByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, WorkspaceMembershipStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByUserId(UUID userId) {
        return delegate.existsByUserIdAndStatus(userId, WorkspaceMembershipStatus.ACTIVE);
    }
}
