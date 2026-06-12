package com.milestoneflow.project.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for updating project information.
 *
 * <p>All fields are optional; only non-null fields are applied.
 */
@Schema(description = "Request body for updating project information")
public record UpdateProjectRequest(

        @Size(min = 1, max = 120, message = "Name must be between 1 and 120 characters")
        @Schema(description = "Project display name", example = "Pilot MVP V0.1")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        @Schema(description = "Project description")
        String description,

        @Schema(description = "Planned start date", example = "2026-06-13")
        LocalDate startDate,

        @Schema(description = "Target completion date", example = "2026-07-20")
        LocalDate targetDate
) {
}
