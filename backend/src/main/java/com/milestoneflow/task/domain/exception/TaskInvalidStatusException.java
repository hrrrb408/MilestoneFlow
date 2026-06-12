package com.milestoneflow.task.domain.exception;

public class TaskInvalidStatusException extends RuntimeException {
    public TaskInvalidStatusException(String invalidValue) {
        super("Invalid task status: " + invalidValue);
    }
}
