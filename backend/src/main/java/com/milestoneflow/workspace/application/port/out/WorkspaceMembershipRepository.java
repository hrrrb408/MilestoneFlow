package com.milestoneflow.workspace.application.port.out;

import com.milestoneflow.workspace.domain.model.WorkspaceMembership;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for workspace membership persistence operations.
 *
 * <p>The application layer depends on this port interface; the infrastructure
 * layer provides the adapter implementation.
 *
 * <p>V0.1 assumes one active membership per user (enforced by
 * {@code uk_workspace_membership_active_user} partial unique index).
 */
public interface WorkspaceMembershipRepository {

    WorkspaceMembership save(WorkspaceMembership membership);

    Optional<WorkspaceMembership> findActiveByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<WorkspaceMembership> findActiveByUserId(UUID userId);

    boolean existsActiveByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsActiveByUserId(UUID userId);
}
