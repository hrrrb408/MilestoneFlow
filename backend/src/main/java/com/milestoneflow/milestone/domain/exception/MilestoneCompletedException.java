package com.milestoneflow.milestone.domain.exception;

/**
 * Thrown when attempting to update a milestone that is in COMPLETED status.
 *
 * <p>The milestone must be reopened before its basic fields can be modified.
 */
public class MilestoneCompletedException extends RuntimeException {

    public MilestoneCompletedException() {
        super("Cannot update a completed milestone. Reopen it first.");
    }
}
