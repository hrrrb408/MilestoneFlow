package com.milestoneflow.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for creating a new task.
 */
@Schema(description = "Request body for creating a new task")
public record CreateTaskRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 1, max = 160, message = "Title must be between 1 and 160 characters")
        @Schema(description = "Task title", example = "Implement user authentication")
        String title,

        @Size(max = 4000, message = "Description must be at most 4000 characters")
        @Schema(description = "Task description", example = "Add login, registration, and token management")
        String description,

        @Schema(description = "Task priority (LOW, MEDIUM, HIGH). Defaults to MEDIUM if not provided.",
                example = "HIGH")
        String priority,

        @Schema(description = "Target completion date", example = "2026-07-15")
        LocalDate dueDate
) {
}
