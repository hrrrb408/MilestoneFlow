package com.milestoneflow.workspace.domain.exception;

/**
 * Thrown when a workspace cannot be found or the current user
 * does not have access to it.
 *
 * <p>Returns the same message regardless of whether the workspace
 * does not exist or the user lacks access, to prevent information leakage.
 */
public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException() {
        super("Workspace not found");
    }
}
