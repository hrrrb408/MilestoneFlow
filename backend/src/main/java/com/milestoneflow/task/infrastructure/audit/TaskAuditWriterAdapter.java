package com.milestoneflow.task.infrastructure.audit;

import com.milestoneflow.audit.application.service.AuditEventWriter;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges the task module's {@link TaskAuditWriter} port
 * to the audit module's {@link AuditEventWriter} service.
 *
 * <p>This adapter resides in task.infrastructure to satisfy ARCH-005
 * (modules must not access other modules' infrastructure). The task
 * application layer only depends on its own port interface.
 *
 * <p>Task audit events carry a non-null {@code workspaceId} in the
 * audit event, with {@code targetType = "TASK"} and {@code targetId = taskId}.
 */
@org.springframework.stereotype.Component
public class TaskAuditWriterAdapter implements TaskAuditWriter {

    private final AuditEventWriter auditEventWriter;

    public TaskAuditWriterAdapter(AuditEventWriter auditEventWriter) {
        this.auditEventWriter = auditEventWriter;
    }

    @Override
    public void writeUserEvent(String action, UUID userId, UUID workspaceId,
                               UUID taskId, String requestId,
                               String summary, Map<String, Object> metadata) {
        auditEventWriter.write(action, userId, "USER", "TASK", taskId,
                workspaceId, requestId, summary, metadata);
    }
}
