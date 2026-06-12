package com.milestoneflow.milestone.domain.type;

/**
 * Milestone status enum for B4-001 minimal implementation.
 *
 * <p>B4-001 uses a simplified two-state model:
 * <ul>
 *   <li>{@link #OPEN} — default status on creation</li>
 *   <li>{@link #COMPLETED} — deferred to B4-002</li>
 * </ul>
 *
 * <p>The full MilestoneStatus state machine from the database design doc
 * (DRAFT → READY → IN_PROGRESS → PENDING_ACCEPTANCE → ACCEPTED/REJECTED, etc.)
 * will be introduced in a later migration when the complete status workflow
 * is implemented.
 *
 * @see "docs/MilestoneFlow_数据库与领域模型_V0.1/05_状态机与枚举说明.md §6"
 */
public enum MilestoneStatus {

    OPEN,
    COMPLETED
}
