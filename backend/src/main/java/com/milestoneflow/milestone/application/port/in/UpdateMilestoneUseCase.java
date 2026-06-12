package com.milestoneflow.milestone.application.port.in;

import com.milestoneflow.milestone.application.command.UpdateMilestoneCommand;
import com.milestoneflow.milestone.application.result.MilestoneResult;

import java.util.UUID;

/**
 * Use case for updating basic milestone information.
 *
 * <p>V0.1 requires OWNER role for milestone updates.
 */
public interface UpdateMilestoneUseCase {

    /**
     * Updates a milestone's basic information.
     *
     * @param command   the update command
     * @param userId    the authenticated user performing the action
     * @param requestId the request correlation ID (nullable)
     * @return the updated milestone result
     */
    MilestoneResult update(UpdateMilestoneCommand command, UUID userId, String requestId);
}
