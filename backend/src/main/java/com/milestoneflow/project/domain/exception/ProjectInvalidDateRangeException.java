package com.milestoneflow.project.domain.exception;

/**
 * Thrown when a project's date range is invalid (e.g., startDate > targetDate).
 */
public class ProjectInvalidDateRangeException extends RuntimeException {

    public ProjectInvalidDateRangeException() {
        super("Start date must not be after target date");
    }
}
