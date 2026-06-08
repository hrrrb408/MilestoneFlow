package com.milestoneflow.identity.infrastructure.audit;

import com.milestoneflow.audit.application.service.AuditEventWriter;
import com.milestoneflow.identity.application.port.out.AuthAuditWriter;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges the identity module's {@link AuthAuditWriter} port
 * to the audit module's {@link AuditEventWriter} service.
 *
 * <p>This adapter resides in identity.infrastructure to satisfy ARCH-005
 * (modules must not access other modules' infrastructure). The identity
 * application layer only depends on its own port interface.
 */
@org.springframework.stereotype.Component
public class AuthAuditWriterAdapter implements AuthAuditWriter {

    private final AuditEventWriter auditEventWriter;

    public AuthAuditWriterAdapter(AuditEventWriter auditEventWriter) {
        this.auditEventWriter = auditEventWriter;
    }

    @Override
    public void writeUserEvent(String action, UUID userId, String targetType,
                               UUID targetId, String requestId, String summary,
                               Map<String, Object> metadata) {
        auditEventWriter.writeUserEvent(action, userId, targetType, targetId,
                requestId, summary, metadata);
    }

    @Override
    public void writeSystemEvent(String action, String targetType, UUID targetId,
                                 String requestId, String summary,
                                 Map<String, Object> metadata) {
        auditEventWriter.writeSystemEvent(action, targetType, targetId,
                requestId, summary, metadata);
    }
}
