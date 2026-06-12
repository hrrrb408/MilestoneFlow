package com.milestoneflow.task.domain.exception;

/**
 * Thrown when attempting to update a task that is in COMPLETED status.
 *
 * <p>The task must be reopened before its basic fields can be modified.
 */
public class TaskCompletedException extends RuntimeException {

    public TaskCompletedException() {
        super("Cannot update a completed task. Reopen it first.");
    }
}
