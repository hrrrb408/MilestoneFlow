package com.milestoneflow.workspace.infrastructure.persistence;

import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * Projects the ACTIVE members of a workspace with safe display info.
     *
     * <p>Native SQL is required because ADR-BE-007 constraint #4 forbids
     * cross-module JPQL joins on entities. The join reads only the safe
     * {@code email} / {@code display_name} columns from {@code app_user}.
     *
     * @param workspaceId the workspace whose members to project
     * @return ACTIVE members ordered by {@code joined_at} ascending
     */
    @Query(value = """
            SELECT m.user_id AS userId,
                   u.email AS email,
                   u.display_name AS displayName,
                   m.role AS role,
                   m.status AS status,
                   m.joined_at AS joinedAt
              FROM workspace_membership m
              JOIN app_user u ON u.id = m.user_id
             WHERE m.workspace_id = :workspaceId
               AND m.status = 'ACTIVE'
             ORDER BY m.joined_at ASC
            """, nativeQuery = true)
    List<WorkspaceMemberProjection> findActiveMembersByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
