package com.milestoneflow.milestone.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for a list of milestones.
 */
@Schema(description = "Milestone list response")
public record MilestoneListResponse(

        @Schema(description = "List of milestones")
        List<MilestoneResponse> items
) {
}
