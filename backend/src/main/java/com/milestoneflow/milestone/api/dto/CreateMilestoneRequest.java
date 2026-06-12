package com.milestoneflow.milestone.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for creating a new milestone.
 */
@Schema(description = "Request body for creating a new milestone")
public record CreateMilestoneRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 1, max = 180, message = "Title must be between 1 and 180 characters")
        @Schema(description = "Milestone title", example = "MVP Authentication Completed")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        @Schema(description = "Milestone description", example = "Finish auth, workspace and project foundations")
        String description,

        @Schema(description = "Target completion date", example = "2026-07-01")
        LocalDate dueDate
) {
}
