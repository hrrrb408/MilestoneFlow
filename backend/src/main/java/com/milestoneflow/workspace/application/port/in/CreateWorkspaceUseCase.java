package com.milestoneflow.workspace.application.port.in;

import com.milestoneflow.workspace.application.command.CreateWorkspaceCommand;
import com.milestoneflow.workspace.application.result.WorkspaceResult;

import java.util.UUID;

/**
 * Use case for creating a new workspace.
 *
 * <p>Creates a workspace with the given name and slug, automatically
 * establishing the creator as OWNER with an ACTIVE membership.
 */
public interface CreateWorkspaceUseCase {

    /**
     * Creates a new workspace and assigns the creator as OWNER.
     *
     * @param command  workspace creation parameters
     * @param userId   the authenticated user creating the workspace
     * @param requestId request correlation ID (nullable)
     * @return the created workspace result
     */
    WorkspaceResult create(CreateWorkspaceCommand command, UUID userId, String requestId);
}
