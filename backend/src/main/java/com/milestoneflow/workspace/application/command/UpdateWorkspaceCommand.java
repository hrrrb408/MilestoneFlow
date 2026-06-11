package com.milestoneflow.workspace.application.command;

import java.util.UUID;

/**
 * Command for updating basic workspace information.
 *
 * @param workspaceId     the workspace to update
 * @param name            new display name (nullable to skip)
 * @param timezone        new timezone (nullable to skip)
 * @param defaultCurrency new currency code (nullable to skip)
 */
public record UpdateWorkspaceCommand(
        UUID workspaceId,
        String name,
        String timezone,
        String defaultCurrency
) {
}
