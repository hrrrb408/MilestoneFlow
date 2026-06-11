package com.milestoneflow.workspace.infrastructure.audit;

import com.milestoneflow.audit.application.service.AuditEventWriter;
import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges the workspace module's {@link WorkspaceAuditWriter} port
 * to the audit module's {@link AuditEventWriter} service.
 *
 * <p>This adapter resides in workspace.infrastructure to satisfy ARCH-005
 * (modules must not access other modules' infrastructure). The workspace
 * application layer only depends on its own port interface.
 *
 * <p>Workspace audit events carry a non-null {@code workspaceId} in the
 * audit event, unlike identity events which have no workspace context.
 */
@org.springframework.stereotype.Component
public class WorkspaceAuditWriterAdapter implements WorkspaceAuditWriter {

    private final AuditEventWriter auditEventWriter;

    public WorkspaceAuditWriterAdapter(AuditEventWriter auditEventWriter) {
        this.auditEventWriter = auditEventWriter;
    }

    @Override
    public void writeUserEvent(String action, UUID userId, UUID workspaceId,
                               String targetType, UUID targetId, String requestId,
                               String summary, Map<String, Object> metadata) {
        auditEventWriter.write(action, userId, "USER", targetType, targetId,
                workspaceId, requestId, summary, metadata);
    }
}
