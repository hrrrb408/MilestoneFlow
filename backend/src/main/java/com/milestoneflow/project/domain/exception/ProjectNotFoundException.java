package com.milestoneflow.project.domain.exception;

/**
 * Thrown when a project is not found or does not belong to the requested workspace.
 *
 * <p>Uses the same message for both cases to prevent project existence leakage
 * across workspace boundaries.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Project not found");
    }
}
