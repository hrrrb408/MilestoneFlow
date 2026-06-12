package com.milestoneflow.project.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Project-module-specific audit port.
 *
 * <p>Provides typed convenience methods for recording project audit events.
 * The project application layer depends on this port rather than the audit
 * module directly.
 *
 * <p>All methods are best-effort: failures are logged but do not propagate.
 */
public interface ProjectAuditWriter {

    /**
     * Records a user-initiated project event.
     *
     * @param action      the event action (e.g., PROJECT_CREATED)
     * @param userId      the user who performed the action
     * @param workspaceId the workspace context
     * @param projectId   the project ID
     * @param requestId   the request correlation ID (nullable)
     * @param summary     human-readable summary
     * @param metadata    additional sanitized context (nullable)
     */
    void writeUserEvent(String action, UUID userId, UUID workspaceId,
                        UUID projectId, String requestId,
                        String summary, Map<String, Object> metadata);
}
