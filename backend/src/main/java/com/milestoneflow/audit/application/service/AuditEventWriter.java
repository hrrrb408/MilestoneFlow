package com.milestoneflow.audit.application.service;

import com.milestoneflow.audit.application.port.out.AuditEventRepository;
import com.milestoneflow.audit.domain.model.AuditEvent;
import com.milestoneflow.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for writing authentication audit events.
 *
 * <p>Provides a typed API for recording authentication events to the
 * {@code audit_event} table. All events use:
 * <ul>
 *   <li>{@code source = API} (all current auth events originate from REST API calls)</li>
 *   <li>{@code actorType = USER} for user-initiated events</li>
 *   <li>{@code actorType = SYSTEM} for system-triggered events</li>
 * </ul>
 *
 * <p>Audit failures are best-effort: if writing fails, a warning is logged
 * with sanitized details and the main business flow continues.
 * The exception is critical security events (e.g., replay detection) where
 * audit failure should still be logged but not block the security action
 * (family revocation already happened in a REQUIRES_NEW transaction).
 */
@Service
public class AuditEventWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditEventWriter.class);

    private final AuditEventRepository auditEventRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AuditEventWriter(AuditEventRepository auditEventRepository,
                            IdGenerator idGenerator,
                            Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Records an authentication audit event.
     *
     * <p>Best-effort: failures are logged but do not propagate to the caller.
     *
     * @param action     the event action (e.g., AUTH_LOGIN_SUCCEEDED)
     * @param actorId    the user ID (nullable for SYSTEM events)
     * @param actorType  USER, SYSTEM, or JOB
     * @param targetType the type of target entity (nullable)
     * @param targetId   the ID of the target entity (nullable)
     * @param requestId  the request correlation ID (nullable)
     * @param summary    human-readable summary
     * @param metadata   additional sanitized context (nullable)
     */
    public void write(String action, UUID actorId, String actorType,
                      String targetType, UUID targetId, String requestId,
                      String summary, Map<String, Object> metadata) {
        try {
            AuditEvent event = AuditEvent.create(
                    idGenerator.nextId(),
                    actorId,
                    actorType,
                    action,
                    targetType,
                    targetId,
                    null, // workspaceId — identity events have no workspace context
                    requestId,
                    "API",
                    summary,
                    metadata,
                    Instant.now(clock)
            );
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Audit write failed: action={}, actorId={}, requestId={}, error={}",
                    action, actorId, requestId, e.getMessage());
        }
    }

    /**
     * Convenience method for user-initiated auth events.
     */
    public void writeUserEvent(String action, UUID userId, String targetType,
                               UUID targetId, String requestId, String summary,
                               Map<String, Object> metadata) {
        write(action, userId, "USER", targetType, targetId, requestId, summary, metadata);
    }

    /**
     * Convenience method for system-initiated auth events.
     */
    public void writeSystemEvent(String action, String targetType, UUID targetId,
                                 String requestId, String summary,
                                 Map<String, Object> metadata) {
        write(action, null, "SYSTEM", targetType, targetId, requestId, summary, metadata);
    }
}
