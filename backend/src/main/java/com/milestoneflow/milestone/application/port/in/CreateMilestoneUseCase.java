package com.milestoneflow.milestone.application.port.in;

import com.milestoneflow.milestone.application.command.CreateMilestoneCommand;
import com.milestoneflow.milestone.application.result.MilestoneResult;

import java.util.UUID;

/**
 * Use case for creating a new milestone within a project.
 */
public interface CreateMilestoneUseCase {

    /**
     * Creates a new milestone.
     *
     * @param command   the creation command
     * @param userId    the authenticated user performing the action
     * @param requestId the request correlation ID (nullable)
     * @return the created milestone result
     */
    MilestoneResult create(CreateMilestoneCommand command, UUID userId, String requestId);
}
