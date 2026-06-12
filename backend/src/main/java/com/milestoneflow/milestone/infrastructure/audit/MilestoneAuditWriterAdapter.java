package com.milestoneflow.milestone.infrastructure.audit;

import com.milestoneflow.audit.application.service.AuditEventWriter;
import com.milestoneflow.milestone.application.port.out.MilestoneAuditWriter;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges the milestone module's {@link MilestoneAuditWriter} port
 * to the audit module's {@link AuditEventWriter} service.
 *
 * <p>This adapter resides in milestone.infrastructure to satisfy ARCH-005
 * (modules must not access other modules' infrastructure). The milestone
 * application layer only depends on its own port interface.
 *
 * <p>Milestone audit events carry a non-null {@code workspaceId} in the
 * audit event, with {@code targetType = "MILESTONE"} and {@code targetId = milestoneId}.
 */
@org.springframework.stereotype.Component
public class MilestoneAuditWriterAdapter implements MilestoneAuditWriter {

    private final AuditEventWriter auditEventWriter;

    public MilestoneAuditWriterAdapter(AuditEventWriter auditEventWriter) {
        this.auditEventWriter = auditEventWriter;
    }

    @Override
    public void writeUserEvent(String action, UUID userId, UUID workspaceId,
                               UUID milestoneId, String requestId,
                               String summary, Map<String, Object> metadata) {
        auditEventWriter.write(action, userId, "USER", "MILESTONE", milestoneId,
                workspaceId, requestId, summary, metadata);
    }
}
