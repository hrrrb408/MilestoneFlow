package com.milestoneflow.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for a list of tasks.
 */
@Schema(description = "Task list response")
public record TaskListResponse(

        @Schema(description = "List of tasks")
        List<TaskResponse> items
) {
}
