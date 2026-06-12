package com.milestoneflow.milestone.domain.exception;

/**
 * Thrown when attempting to reopen a milestone that is not COMPLETED.
 */
public class MilestoneNotCompletedException extends RuntimeException {

    public MilestoneNotCompletedException() {
        super("Milestone is not completed");
    }
}
