package com.milestoneflow.milestone.infrastructure.persistence;

import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.milestone.domain.type.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Milestone entities.
 *
 * <p>Package-private per ARCH-010. Accessed exclusively through
 * {@link MilestoneRepositoryAdapter}.
 */
interface SpringDataMilestoneRepository extends JpaRepository<Milestone, UUID> {

    Optional<Milestone> findByWorkspaceIdAndProjectIdAndId(UUID workspaceId, UUID projectId, UUID id);

    @Query("SELECT m FROM Milestone m " +
            "WHERE m.workspaceId = :workspaceId AND m.projectId = :projectId " +
            "ORDER BY m.dueDate ASC NULLS LAST, m.createdAt ASC")
    List<Milestone> findAllByWorkspaceIdAndProjectIdOrdered(UUID workspaceId, UUID projectId);

    @Query("SELECT m FROM Milestone m " +
            "WHERE m.workspaceId = :workspaceId AND m.projectId = :projectId AND m.status = :status " +
            "ORDER BY m.dueDate ASC NULLS LAST, m.createdAt ASC")
    List<Milestone> findByWorkspaceIdAndProjectIdAndStatusOrdered(UUID workspaceId, UUID projectId, MilestoneStatus status);
}
