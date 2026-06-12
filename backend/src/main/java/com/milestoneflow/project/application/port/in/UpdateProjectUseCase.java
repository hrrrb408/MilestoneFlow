package com.milestoneflow.project.application.port.in;

import com.milestoneflow.project.application.command.UpdateProjectCommand;
import com.milestoneflow.project.application.result.ProjectResult;

import java.util.UUID;

/**
 * Use case port for updating a project.
 */
public interface UpdateProjectUseCase {

    /**
     * Updates basic project information.
     *
     * @param command   the update command
     * @param userId    the authenticated user
     * @param requestId the request correlation ID
     * @return the updated project result
     */
    ProjectResult update(UpdateProjectCommand command, UUID userId, String requestId);
}
