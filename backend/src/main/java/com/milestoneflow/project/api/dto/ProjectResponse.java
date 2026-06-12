package com.milestoneflow.project.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Response DTO for a single project.
 *
 * <p>Does not expose internal auditing fields (createdBy, updatedBy),
 * version, settings, or archivedAt.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Project response")
public record ProjectResponse(

        @Schema(description = "Project unique identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String id,

        @Schema(description = "Owning workspace identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String workspaceId,

        @Schema(description = "Project display name", example = "Pilot MVP")
        String name,

        @Schema(description = "Project description")
        String description,

        @Schema(description = "Project status", example = "ACTIVE")
        String status,

        @Schema(description = "Planned start date", example = "2026-06-12")
        LocalDate startDate,

        @Schema(description = "Target completion date", example = "2026-07-12")
        LocalDate targetDate,

        @Schema(description = "Creation timestamp")
        String createdAt,

        @Schema(description = "Last update timestamp")
        String updatedAt
) {
}
