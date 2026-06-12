package com.milestoneflow.workspace.domain.type;

/**
 * Workspace lifecycle status.
 *
 * <p>Mapped to the {@code workspace.status} CHECK constraint in V003:
 * {@code CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))}.
 *
 * <p>V0.1 only supports {@code ACTIVE} — SUSPENDED and ARCHIVED are reserved
 * for future use and not exposed via API.
 */
public enum WorkspaceStatus {

    ACTIVE,
    SUSPENDED,
    ARCHIVED
}
