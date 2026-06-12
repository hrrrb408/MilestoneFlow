package com.milestoneflow.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for creating a new project.
 */
@Schema(description = "Request body for creating a new project")
public record CreateProjectRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 120, message = "Name must be between 1 and 120 characters")
        @Schema(description = "Project display name", example = "Pilot MVP")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        @Schema(description = "Project description", example = "MilestoneFlow Pilot MVP project")
        String description,

        @Schema(description = "Planned start date", example = "2026-06-12")
        LocalDate startDate,

        @Schema(description = "Target completion date", example = "2026-07-12")
        LocalDate targetDate
) {
}
