package com.milestoneflow.task.domain.exception;

public class TaskInvalidPriorityException extends RuntimeException {
    public TaskInvalidPriorityException(String invalidValue) {
        super("Invalid task priority: " + invalidValue);
    }
}
