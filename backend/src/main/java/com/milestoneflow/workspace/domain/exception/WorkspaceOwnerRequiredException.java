package com.milestoneflow.workspace.domain.exception;

/**
 * Thrown when a non-OWNER member attempts an OWNER-only operation
 * (e.g., updating workspace info).
 */
public class WorkspaceOwnerRequiredException extends RuntimeException {

    public WorkspaceOwnerRequiredException() {
        super("Only the workspace owner can perform this action");
    }
}
