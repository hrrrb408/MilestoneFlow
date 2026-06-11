package com.milestoneflow.workspace.application.command;

/**
 * Command for creating a new workspace.
 *
 * @param name            workspace display name
 * @param slug            URL-friendly unique identifier
 * @param timezone        IANA timezone ID (nullable — defaults applied by service)
 * @param defaultCurrency 3-letter uppercase currency code (nullable — defaults applied by service)
 */
public record CreateWorkspaceCommand(
        String name,
        String slug,
        String timezone,
        String defaultCurrency
) {
}
