package com.milestoneflow.workspace.domain.exception;

/**
 * Thrown when a user attempts to access or modify a workspace
 * without the required membership or role.
 */
public class WorkspaceAccessDeniedException extends RuntimeException {

    public WorkspaceAccessDeniedException() {
        super("Access denied to workspace");
    }
}
