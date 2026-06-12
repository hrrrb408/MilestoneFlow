package com.milestoneflow.milestone.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Milestone-module-specific audit port.
 *
 * <p>Provides typed convenience methods for recording milestone audit events.
 * The milestone application layer depends on this port rather than the audit
 * module directly.
 *
 * <p>All methods are best-effort: failures are logged but do not propagate.
 */
public interface MilestoneAuditWriter {

    /**
     * Records a user-initiated milestone event.
     *
     * @param action      the event action (e.g., MILESTONE_CREATED)
     * @param userId      the user who performed the action
     * @param workspaceId the workspace context
     * @param milestoneId the milestone ID
     * @param requestId   the request correlation ID (nullable)
     * @param summary     human-readable summary
     * @param metadata    additional sanitized context (nullable)
     */
    void writeUserEvent(String action, UUID userId, UUID workspaceId,
                        UUID milestoneId, String requestId,
                        String summary, Map<String, Object> metadata);
}
