package com.milestoneflow.task.domain.exception;

/**
 * Thrown when attempting to reopen a task that is not COMPLETED.
 */
public class TaskNotCompletedException extends RuntimeException {

    public TaskNotCompletedException() {
        super("Task is not completed");
    }
}
