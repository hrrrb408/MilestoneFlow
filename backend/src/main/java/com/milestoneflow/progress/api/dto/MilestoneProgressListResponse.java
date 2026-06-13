package com.milestoneflow.progress.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for the milestone progress list endpoint.
 *
 * <p>Contains progress entries for all milestones in a project.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "List of milestone progress entries for a project")
public record MilestoneProgressListResponse(

        @Schema(description = "Milestone progress entries")
        List<MilestoneProgressResponse> items
) {
}
