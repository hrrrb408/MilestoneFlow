package com.milestoneflow.project.application.port.out;

import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.project.domain.type.ProjectStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for project persistence.
 *
 * <p>Application layer depends on this port; infrastructure provides the adapter.
 * Per ADR-BE-006: all query methods include workspaceId as the first parameter
 * to enforce tenant isolation at the repository level.
 */
public interface ProjectRepository {

    /**
     * Saves a project (insert or update).
     * Must return the managed entity with JPA auditing fields populated.
     *
     * @param project the project to save
     * @return the managed entity with auditing fields
     */
    Project save(Project project);

    /**
     * Finds a project by workspace ID and project ID.
     *
     * <p>Uses composite key lookup to prevent cross-workspace access.
     *
     * @param workspaceId the workspace scope
     * @param projectId   the project ID
     * @return the project if found and belongs to workspace
     */
    Optional<Project> findByWorkspaceIdAndId(UUID workspaceId, UUID projectId);

    /**
     * Finds all active projects in a workspace, ordered by creation time descending.
     *
     * @param workspaceId the workspace scope
     * @return list of active projects
     */
    List<Project> findActiveByWorkspaceId(UUID workspaceId);

    /**
     * Finds all projects in a workspace (including archived), ordered by creation time descending.
     *
     * @param workspaceId the workspace scope
     * @return list of all projects
     */
    List<Project> findByWorkspaceId(UUID workspaceId);

    /**
     * Finds projects in a workspace matching the given statuses, ordered by creation time descending.
     *
     * @param workspaceId the workspace scope
     * @param statuses    the statuses to filter by
     * @return list of matching projects
     */
    List<Project> findByWorkspaceIdAndStatuses(UUID workspaceId, Collection<ProjectStatus> statuses);
}
