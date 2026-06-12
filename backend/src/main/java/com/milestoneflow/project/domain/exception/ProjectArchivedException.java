package com.milestoneflow.project.domain.exception;

/**
 * Thrown when attempting to modify an archived project.
 */
public class ProjectArchivedException extends RuntimeException {

    public ProjectArchivedException() {
        super("Project is archived and cannot be modified");
    }
}
