package com.milestoneflow.activity.application.result;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-model row representing a single activity log entry.
 *
 * <p>Mapped from the {@code audit_event} table. The database column
 * {@code action} is exposed as {@code eventType} for API-friendly naming.
 * This record lives in the application layer and is never returned directly
 * to the API — the controller maps it to an {@code ActivityLogResponse} DTO.
 *
 * @param id          audit event primary key (UUID v7)
 * @param workspaceId workspace scope (nullable for identity events)
 * @param actorId     user who performed the action (nullable for SYSTEM/JOB)
 * @param actorType   USER, SYSTEM, or JOB
 * @param eventType   the action performed (e.g., TASK_CREATED)
 * @param targetType  the entity type acted upon (e.g., TASK, PROJECT)
 * @param targetId    the entity UUID acted upon
 * @param summary     human-readable description of the event
 * @param metadata    additional event context (sanitised for sensitive keys on write)
 * @param createdAt   event timestamp
 */
public record ActivityLogRow(
        UUID id,
        UUID workspaceId,
        UUID actorId,
        String actorType,
        String eventType,
        String targetType,
        UUID targetId,
        String summary,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
