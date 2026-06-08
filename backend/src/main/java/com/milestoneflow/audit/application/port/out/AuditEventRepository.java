package com.milestoneflow.audit.application.port.out;

import com.milestoneflow.audit.domain.model.AuditEvent;

/**
 * Output port for persisting audit events.
 *
 * <p>Append-only: only {@link #save} is supported.
 * No update or delete operations are exposed.
 * The database enforces append-only via triggers (V004).
 */
public interface AuditEventRepository {

    /**
     * Persists a new audit event.
     *
     * @param event the audit event to persist (must not have been persisted before)
     */
    void save(AuditEvent event);
}
