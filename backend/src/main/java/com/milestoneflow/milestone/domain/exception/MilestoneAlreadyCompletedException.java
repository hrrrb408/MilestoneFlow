package com.milestoneflow.milestone.domain.exception;

/**
 * Thrown when attempting to complete a milestone that is already COMPLETED.
 */
public class MilestoneAlreadyCompletedException extends RuntimeException {

    public MilestoneAlreadyCompletedException() {
        super("Milestone is already completed");
    }
}
