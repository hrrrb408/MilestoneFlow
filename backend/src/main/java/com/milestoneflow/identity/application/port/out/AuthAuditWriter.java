package com.milestoneflow.identity.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Identity-module-specific audit port.
 *
 * <p>Provides typed convenience methods for recording authentication audit events.
 * The identity application layer depends on this port rather than the audit module directly.
 *
 * <p>All methods are best-effort: failures are logged but do not propagate.
 */
public interface AuthAuditWriter {

    /**
     * Records a user-initiated authentication event.
     *
     * @param action     the event action (e.g., AUTH_LOGIN_SUCCEEDED)
     * @param userId     the user who performed the action (nullable for anonymous)
     * @param targetType the type of target entity (nullable)
     * @param targetId   the ID of the target entity (nullable)
     * @param requestId  the request correlation ID (nullable)
     * @param summary    human-readable summary
     * @param metadata   additional sanitized context (nullable)
     */
    void writeUserEvent(String action, UUID userId, String targetType,
                        UUID targetId, String requestId, String summary,
                        Map<String, Object> metadata);

    /**
     * Records a system-initiated authentication event.
     *
     * @param action     the event action (e.g., AUTH_RATE_LIMIT_REJECTED)
     * @param targetType the type of target entity (nullable)
     * @param targetId   the ID of the target entity (nullable)
     * @param requestId  the request correlation ID (nullable)
     * @param summary    human-readable summary
     * @param metadata   additional sanitized context (nullable)
     */
    void writeSystemEvent(String action, String targetType, UUID targetId,
                          String requestId, String summary,
                          Map<String, Object> metadata);
}
