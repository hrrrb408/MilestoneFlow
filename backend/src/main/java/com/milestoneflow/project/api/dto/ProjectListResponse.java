package com.milestoneflow.project.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for project list.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Project list response")
public record ProjectListResponse(

        @Schema(description = "List of projects")
        List<ProjectResponse> items
) {
}
