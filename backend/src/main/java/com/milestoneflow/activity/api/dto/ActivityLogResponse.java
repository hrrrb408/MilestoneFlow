package com.milestoneflow.activity.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Response DTO for a single activity log entry.
 *
 * <p>Mapped from {@link com.milestoneflow.activity.application.result.ActivityLogRow}.
 * All identifiers are exposed as strings for JSON compatibility.
 * The {@code eventType} field corresponds to the {@code audit_event.action} column.
 *
 * <p>Metadata is sanitised at audit write time — forbidden sensitive keys
 * (password, token, cookie, etc.) are rejected before persistence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single activity log entry")
public record ActivityLogResponse(

        @Schema(description = "Activity event identifier", example = "0192a...")
        String id,

        @Schema(description = "Workspace identifier", example = "0192b...")
        String workspaceId,

        @Schema(description = "Actor user identifier (null for SYSTEM/JOB events)", example = "0192c...")
        String actorId,

        @Schema(description = "Actor type: USER, SYSTEM, or JOB", example = "USER")
        String actorType,

        @Schema(description = "Event type (e.g., TASK_CREATED, PROJECT_UPDATED)", example = "TASK_CREATED")
        String eventType,

        @Schema(description = "Target entity type (e.g., TASK, MILESTONE, PROJECT)", example = "TASK")
        String targetType,

        @Schema(description = "Target entity identifier", example = "0192d...")
        String targetId,

        @Schema(description = "Human-readable summary of the event", example = "Task created")
        String summary,

        @Schema(description = "Additional event context (sanitised for sensitive data)")
        Map<String, Object> metadata,

        @Schema(description = "Event timestamp", example = "2026-06-13T10:00:00Z")
        OffsetDateTime createdAt
) {
}
