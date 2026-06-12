package com.milestoneflow.task.domain.exception;

/**
 * Thrown when attempting to complete a task that is already COMPLETED.
 */
public class TaskAlreadyCompletedException extends RuntimeException {

    public TaskAlreadyCompletedException() {
        super("Task is already completed");
    }
}
