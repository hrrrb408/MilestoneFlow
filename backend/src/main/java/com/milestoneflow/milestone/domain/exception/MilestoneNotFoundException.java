package com.milestoneflow.milestone.domain.exception;

/**
 * Thrown when a milestone is not found, does not belong to the specified
 * project, or does not belong to the specified workspace.
 *
 * <p>Returns a generic message to prevent resource existence leakage across
 * projects and workspaces.
 */
public class MilestoneNotFoundException extends RuntimeException {

    public MilestoneNotFoundException() {
        super("Milestone not found");
    }
}
