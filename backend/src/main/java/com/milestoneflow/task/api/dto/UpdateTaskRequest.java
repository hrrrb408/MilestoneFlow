package com.milestoneflow.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for updating task information.
 *
 * <p>All fields are optional; only non-null fields are applied.
 */
@Schema(description = "Request body for updating task information")
public record UpdateTaskRequest(

        @Size(min = 1, max = 160, message = "Title must be between 1 and 160 characters")
        @Schema(description = "Task title", example = "Implement user authentication and authorization")
        String title,

        @Size(max = 4000, message = "Description must be at most 4000 characters")
        @Schema(description = "Task description")
        String description,

        @Schema(description = "Task priority (LOW, MEDIUM, HIGH)", example = "MEDIUM")
        String priority,

        @Schema(description = "Target completion date", example = "2026-07-20")
        LocalDate dueDate
) {
}
