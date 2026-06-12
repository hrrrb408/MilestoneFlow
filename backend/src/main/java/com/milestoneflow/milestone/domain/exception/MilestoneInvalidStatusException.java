package com.milestoneflow.milestone.domain.exception;

/**
 * Thrown when an invalid milestone status value is provided.
 */
public class MilestoneInvalidStatusException extends RuntimeException {

    public MilestoneInvalidStatusException(String invalidValue) {
        super("Invalid milestone status: " + invalidValue);
    }
}
