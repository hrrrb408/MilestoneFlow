package com.milestoneflow.workspace.application.port.in;

import com.milestoneflow.workspace.application.command.UpdateWorkspaceCommand;
import com.milestoneflow.workspace.application.result.WorkspaceResult;

import java.util.UUID;

/**
 * Use case for updating basic workspace information.
 *
 * <p>Requires the caller to be the OWNER of the workspace.
 * V0.1 does not allow slug changes.
 */
public interface UpdateWorkspaceUseCase {

    /**
     * Updates workspace basic info (name, timezone, currency).
     *
     * @param command   update parameters (workspaceId + optional fields)
     * @param userId    the authenticated user (must be OWNER)
     * @param requestId request correlation ID (nullable)
     * @return the updated workspace result
     */
    WorkspaceResult update(UpdateWorkspaceCommand command, UUID userId, String requestId);
}
