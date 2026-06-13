package com.milestoneflow.progress.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for milestone progress queries.
 *
 * <p>Carries aggregated task counts and completion rate for a single milestone.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Milestone progress with task counts")
public record MilestoneProgressResponse(

        @Schema(description = "Workspace identifier", example = "0192a...")
        String workspaceId,

        @Schema(description = "Project identifier", example = "0192b...")
        String projectId,

        @Schema(description = "Milestone identifier", example = "0192c...")
        String milestoneId,

        @Schema(description = "Milestone title", example = "Backend MVP")
        String milestoneTitle,

        @Schema(description = "Milestone status", example = "OPEN",
                allowableValues = {"OPEN", "COMPLETED"})
        String milestoneStatus,

        @Schema(description = "Total tasks in the milestone", example = "5")
        long totalTasks,

        @Schema(description = "Completed tasks", example = "2")
        long completedTasks,

        @Schema(description = "Open tasks", example = "3")
        long openTasks,

        @Schema(description = "Completion rate (0.00 – 100.00)", example = "40.00")
        java.math.BigDecimal completionRate
) {
}
