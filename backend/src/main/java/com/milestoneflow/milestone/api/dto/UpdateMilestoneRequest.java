package com.milestoneflow.milestone.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for updating milestone information.
 *
 * <p>All fields are optional; only non-null fields are applied.
 */
@Schema(description = "Request body for updating milestone information")
public record UpdateMilestoneRequest(

        @Size(min = 1, max = 180, message = "Title must be between 1 and 180 characters")
        @Schema(description = "Milestone title", example = "MVP Core Backend Completed")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        @Schema(description = "Milestone description")
        String description,

        @Schema(description = "Target completion date", example = "2026-07-10")
        LocalDate dueDate
) {
}
