package com.milestoneflow.project.domain.type;

/**
 * Status values for a project.
 *
 * <p>V0.1 supports only ACTIVE and ARCHIVED. Future versions may add
 * PLANNED and COMPLETED via a status workflow migration.
 *
 * <p>Must match the CHECK constraint in V008__project.sql.
 */
public enum ProjectStatus {

    ACTIVE,
    ARCHIVED
}
