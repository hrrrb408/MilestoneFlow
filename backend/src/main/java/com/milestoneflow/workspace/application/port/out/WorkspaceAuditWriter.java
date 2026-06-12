package com.milestoneflow.workspace.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Workspace-module-specific audit port.
 *
 * <p>Provides typed convenience methods for recording workspace audit events.
 * The workspace application layer depends on this port rather than the audit
 * module directly.
 *
 * <p>All methods are best-effort: failures are logged but do not propagate.
 */
public interface WorkspaceAuditWriter {

    /**
     * Records a user-initiated workspace event.
     *
     * @param action      the event action (e.g., WORKSPACE_CREATED)
     * @param userId      the user who performed the action
     * @param workspaceId the workspace context
     * @param targetType  the type of target entity (nullable)
     * @param targetId    the ID of the target entity (nullable)
     * @param requestId   the request correlation ID (nullable)
     * @param summary     human-readable summary
     * @param metadata    additional sanitized context (nullable)
     */
    void writeUserEvent(String action, UUID userId, UUID workspaceId,
                        String targetType, UUID targetId, String requestId,
                        String summary, Map<String, Object> metadata);
}
