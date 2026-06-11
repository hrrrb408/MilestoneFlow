package com.milestoneflow.workspace.domain.exception;

/**
 * Thrown when attempting to create a workspace with a slug
 * that is already taken by another workspace.
 */
public class WorkspaceSlugAlreadyExistsException extends RuntimeException {

    public WorkspaceSlugAlreadyExistsException() {
        super("Workspace slug is already taken");
    }
}
