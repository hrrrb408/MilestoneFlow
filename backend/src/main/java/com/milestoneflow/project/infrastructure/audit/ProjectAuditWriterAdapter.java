package com.milestoneflow.project.infrastructure.audit;

import com.milestoneflow.audit.application.service.AuditEventWriter;
import com.milestoneflow.project.application.port.out.ProjectAuditWriter;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges the project module's {@link ProjectAuditWriter} port
 * to the audit module's {@link AuditEventWriter} service.
 *
 * <p>This adapter resides in project.infrastructure to satisfy ARCH-005
 * (modules must not access other modules' infrastructure). The project
 * application layer only depends on its own port interface.
 *
 * <p>Project audit events carry a non-null {@code workspaceId} in the
 * audit event, with {@code targetType = "PROJECT"} and {@code targetId = projectId}.
 */
@org.springframework.stereotype.Component
public class ProjectAuditWriterAdapter implements ProjectAuditWriter {

    private final AuditEventWriter auditEventWriter;

    public ProjectAuditWriterAdapter(AuditEventWriter auditEventWriter) {
        this.auditEventWriter = auditEventWriter;
    }

    @Override
    public void writeUserEvent(String action, UUID userId, UUID workspaceId,
                               UUID projectId, String requestId,
                               String summary, Map<String, Object> metadata) {
        auditEventWriter.write(action, userId, "USER", "PROJECT", projectId,
                workspaceId, requestId, summary, metadata);
    }
}
