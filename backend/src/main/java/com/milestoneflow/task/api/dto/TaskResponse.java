package com.milestoneflow.task.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Response DTO for a single task.
 *
 * <p>Does not expose internal auditing fields (createdBy, updatedBy),
 * version, settings, completedBy.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Task response")
public record TaskResponse(

        @Schema(description = "Task unique identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String id,

        @Schema(description = "Owning workspace identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String workspaceId,

        @Schema(description = "Parent project identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String projectId,

        @Schema(description = "Parent milestone identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String milestoneId,

        @Schema(description = "Task title", example = "Implement user authentication")
        String title,

        @Schema(description = "Task description")
        String description,

        @Schema(description = "Task status", example = "OPEN")
        String status,

        @Schema(description = "Task priority", example = "HIGH")
        String priority,

        @Schema(description = "Target completion date", example = "2026-07-15")
        LocalDate dueDate,

        @Schema(description = "Completion timestamp (set when status is COMPLETED)")
        String completedAt,

        @Schema(description = "Creation timestamp")
        String createdAt,

        @Schema(description = "Last update timestamp")
        String updatedAt
) {
}
