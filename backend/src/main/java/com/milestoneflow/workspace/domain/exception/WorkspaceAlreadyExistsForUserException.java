package com.milestoneflow.workspace.domain.exception;

/**
 * Thrown when a user who already has an ACTIVE workspace membership
 * attempts to create another workspace.
 *
 * <p>V0.1 enforces one active workspace per user via the partial unique index
 * {@code uk_workspace_membership_active_user} on {@code workspace_membership}.
 * This exception is checked before the database constraint to provide
 * a clear business error message.
 */
public class WorkspaceAlreadyExistsForUserException extends RuntimeException {

    public WorkspaceAlreadyExistsForUserException() {
        super("User already has an active workspace");
    }
}
