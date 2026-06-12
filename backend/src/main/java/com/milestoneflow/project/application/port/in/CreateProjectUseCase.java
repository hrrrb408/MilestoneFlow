package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.command.CreateProjectCommand;
import com.milestoneflow.project.application.result.ProjectResult;

import java.util.UUID;

/**
 * Use case port for creating a new project.
 */
public interface CreateProjectUseCase {

    /**
     * Creates a new project within the specified workspace.
     *
     * @param command   the creation command
     * @param userId    the authenticated user creating the project
     * @param requestId the request correlation ID
     * @return the created project result
     */
    ProjectResult create(CreateProjectCommand command, UUID userId, String requestId);
}
