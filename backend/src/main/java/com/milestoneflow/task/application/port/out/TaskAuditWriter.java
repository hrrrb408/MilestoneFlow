package com.milestoneflow.task.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface TaskAuditWriter {

    void writeUserEvent(String action, UUID userId, UUID workspaceId,
                        UUID taskId, String requestId,
                        String summary, Map<String, Object> metadata);
}
