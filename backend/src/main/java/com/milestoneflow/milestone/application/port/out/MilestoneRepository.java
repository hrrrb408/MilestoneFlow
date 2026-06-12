package com.milestoneflow.milestone.application.port.out;

import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.milestone.domain.type.MilestoneStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for milestone persistence.
 *
 * <p>Application layer depends on this port; infrastructure provides the adapter.
 * Per ADR-BE-006: all query methods include workspaceId as the first parameter
 * to enforce tenant isolation at the repository level.
 */
public interface MilestoneRepository {

    /**
     * Saves a milestone (insert or update).
     * Must return the managed entity with JPA auditing fields populated.
     *
     * @param milestone the milestone to save
     * @return the managed entity with auditing fields
     */
    Milestone save(Milestone milestone);

    /**
     * Finds a milestone by its composite key (workspace + project + milestone).
     *
     * <p>Uses composite key lookup to prevent cross-project and cross-workspace access.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param milestoneId the milestone ID
     * @return the milestone if found and belongs to the specified project and workspace
     */
    Optional<Milestone> findByWorkspaceIdAndProjectIdAndId(UUID workspaceId, UUID projectId, UUID milestoneId);

    /**
     * Finds all milestones in a project, ordered by due date ASC NULLS LAST,
     * then by creation time ASC.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @return list of milestones
     */
    List<Milestone> findByWorkspaceIdAndProjectId(UUID workspaceId, UUID projectId);

    /**
     * Finds milestones in a project filtered by status, ordered by due date
     * ASC NULLS LAST, then by creation time ASC.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project scope
     * @param status      the status to filter by
     * @return list of matching milestones
     */
    List<Milestone> findByWorkspaceIdAndProjectIdAndStatus(UUID workspaceId, UUID projectId, MilestoneStatus status);
}
