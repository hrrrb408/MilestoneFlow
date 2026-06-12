package com.milestoneflow.workspace.application.port.out;

import com.milestoneflow.workspace.application.result.WorkspaceMemberResult;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;

import java.util.List;
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

    /**
     * Lists the ACTIVE members of a workspace, enriched with the safe display
     * fields (email, displayName) from {@code app_user}.
     *
     * <p>This is a read-side projection (per ADR-BE-007): a single native-SQL
     * join across {@code workspace_membership} and {@code app_user}. It returns
     * only ACTIVE memberships, ordered by {@code joined_at} ascending, and never
     * carries sensitive fields ({@code passwordHash}, {@code emailNormalized},
     * token material).
     *
     * @param workspaceId the workspace whose members to list
     * @return the ACTIVE members with safe display info, ordered by {@code joinedAt} ascending
     */
    List<WorkspaceMemberResult> findActiveMembersByWorkspaceId(UUID workspaceId);
}
