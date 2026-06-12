package com.milestoneflow.workspace.domain.type;

/**
 * Status of a user's membership in a workspace.
 *
 * <p>Mapped to the {@code workspace_membership.status} CHECK constraint in V003:
 * {@code CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED'))}.
 *
 * <p>V0.1 only creates memberships in ACTIVE status.
 * PENDING is reserved for future invitation flows.
 * REMOVED marks memberships that are no longer usable.
 */
public enum WorkspaceMembershipStatus {

    PENDING,
    ACTIVE,
    REMOVED
}
