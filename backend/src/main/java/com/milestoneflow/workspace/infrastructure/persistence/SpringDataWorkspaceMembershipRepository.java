package com.milestoneflow.workspace.infrastructure.persistence;

import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkspaceMembership} entities.
 *
 * <p>Package-private per ARCH-010: Spring Data Repository interfaces must not be public.
 * Accessed exclusively through {@link WorkspaceMembershipRepositoryAdapter}.
 */
interface SpringDataWorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserIdAndStatus(
            UUID workspaceId, UUID userId, WorkspaceMembershipStatus status);

    Optional<WorkspaceMembership> findByUserIdAndStatus(UUID userId, WorkspaceMembershipStatus status);

    boolean existsByWorkspaceIdAndUserIdAndStatus(
            UUID workspaceId, UUID userId, WorkspaceMembershipStatus status);

    boolean existsByUserIdAndStatus(UUID userId, WorkspaceMembershipStatus status);
}
