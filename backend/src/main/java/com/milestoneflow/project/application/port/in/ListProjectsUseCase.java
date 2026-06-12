package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.result.ProjectResult;

import java.util.List;
import java.util.UUID;

/**
 * Use case port for listing projects in a workspace.
 */
public interface ListProjectsUseCase {

    /**
     * Lists active projects in the specified workspace.
     *
     * @param workspaceId the workspace to list projects for
     * @param userId      the authenticated user
     * @param requestId   the request correlation ID
     * @return list of project results
     * @deprecated Use {@link #listProjects(UUID, UUID, String, Boolean, String)} instead.
     */
    @Deprecated
    List<ProjectResult> listActiveProjects(UUID workspaceId, UUID userId, String requestId);

    /**
     * Lists projects in the specified workspace with optional status filtering.
     *
     * <p>Filtering rules:
     * <ul>
     *   <li>{@code status} takes priority over {@code includeArchived}.</li>
     *   <li>If {@code status} is set, only projects with that status are returned.</li>
     *   <li>If {@code status} is null and {@code includeArchived} is true, both ACTIVE and ARCHIVED are returned.</li>
     *   <li>If {@code status} is null and {@code includeArchived} is false (default), only ACTIVE are returned.</li>
     * </ul>
     *
     * @param workspaceId     the workspace to list projects for
     * @param userId          the authenticated user
     * @param requestId       the request correlation ID
     * @param includeArchived whether to include archived projects (default false)
     * @param status          specific status filter (nullable, takes priority)
     * @return list of project results
     */
    List<ProjectResult> listProjects(UUID workspaceId, UUID userId, String requestId,
                                     Boolean includeArchived, String status);
}
