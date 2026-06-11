package com.milestoneflow.workspace.domain.type;

/**
 * Role of a user within a workspace.
 *
 * <p>Mapped to the {@code workspace_membership.role} CHECK constraint in V003:
 * {@code CHECK (role IN ('OWNER'))}.
 *
 * <p>V0.1 only includes OWNER. Additional roles (ADMIN, MEMBER) will be
 * added in a future migration when member invitation is implemented.
 */
public enum WorkspaceMembershipRole {

    OWNER
}
