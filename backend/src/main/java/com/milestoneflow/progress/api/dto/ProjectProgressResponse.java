package com.milestoneflow.progress.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for project progress queries.
 *
 * <p>Carries aggregated task counts, milestone counts, and completion rate.
 * The completion rate is based on task-level aggregation across all milestones.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Project progress with task and milestone counts")
public record ProjectProgressResponse(

        @Schema(description = "Workspace identifier", example = "0192a...")
        String workspaceId,

        @Schema(description = "Project identifier", example = "0192b...")
        String projectId,

        @Schema(description = "Total tasks in the project", example = "10")
        long totalTasks,

        @Schema(description = "Completed tasks", example = "4")
        long completedTasks,

        @Schema(description = "Open tasks", example = "6")
        long openTasks,

        @Schema(description = "Completion rate (0.00 – 100.00)", example = "40.00")
        java.math.BigDecimal completionRate,

        @Schema(description = "Total milestones in the project", example = "3")
        long totalMilestones,

        @Schema(description = "Completed milestones", example = "1")
        long completedMilestones,

        @Schema(description = "Open milestones", example = "2")
        long openMilestones
) {
}
