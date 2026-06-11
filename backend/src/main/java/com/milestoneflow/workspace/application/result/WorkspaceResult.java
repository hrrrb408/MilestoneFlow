package com.milestoneflow.workspace.application.result;

import com.milestoneflow.workspace.domain.type.WorkspaceMembershipRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of workspace creation, lookup, or update.
 *
 * <p>Carries workspace data plus the current user's role in that workspace.
 * Used as the return type for all workspace use cases.
 *
 * @param workspaceId     workspace primary key
 * @param name            workspace display name
 * @param slug            URL-friendly identifier
 * @param status          workspace status (e.g., "ACTIVE")
 * @param timezone        IANA timezone ID
 * @param defaultCurrency 3-letter currency code
 * @param role            current user's membership role
 * @param createdAt       workspace creation timestamp
 */
public record WorkspaceResult(
        UUID workspaceId,
        String name,
        String slug,
        String status,
        String timezone,
        String defaultCurrency,
        WorkspaceMembershipRole role,
        Instant createdAt
) {
}
