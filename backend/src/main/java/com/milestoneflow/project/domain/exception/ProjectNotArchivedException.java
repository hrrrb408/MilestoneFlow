package com.milestoneflow.project.domain.exception;

/**
 * Thrown when attempting to restore a project that is not in ARCHIVED status.
 */
public class ProjectNotArchivedException extends RuntimeException {

    public ProjectNotArchivedException() {
        super("Project is not archived and cannot be restored");
    }
}
