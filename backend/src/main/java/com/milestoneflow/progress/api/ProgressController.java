package com.milestoneflow.progress.api;

import com.milestoneflow.progress.api.dto.MilestoneProgressListResponse;
import com.milestoneflow.progress.api.dto.MilestoneProgressResponse;
import com.milestoneflow.progress.api.dto.ProjectProgressResponse;
import com.milestoneflow.progress.application.port.in.GetMilestoneProgressUseCase;
import com.milestoneflow.progress.application.port.in.GetProjectProgressUseCase;
import com.milestoneflow.progress.application.port.in.ListMilestoneProgressUseCase;
import com.milestoneflow.progress.application.result.MilestoneProgressResult;
import com.milestoneflow.progress.application.result.ProjectProgressResult;
import com.milestoneflow.shared.api.ApiResponse;
import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for project and milestone progress queries.
 *
 * <p>All endpoints require authentication via MF_ACCESS cookie.
 * Progress endpoints are read-only — no CSRF token required for GET.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@Tag(name = "Progress", description = "Project and milestone progress APIs")
@SecurityRequirement(name = "cookieAuth")
public class ProgressController {

    private final GetProjectProgressUseCase getProjectProgressUseCase;
    private final GetMilestoneProgressUseCase getMilestoneProgressUseCase;
    private final ListMilestoneProgressUseCase listMilestoneProgressUseCase;

    public ProgressController(GetProjectProgressUseCase getProjectProgressUseCase,
                              GetMilestoneProgressUseCase getMilestoneProgressUseCase,
                              ListMilestoneProgressUseCase listMilestoneProgressUseCase) {
        this.getProjectProgressUseCase = getProjectProgressUseCase;
        this.getMilestoneProgressUseCase = getMilestoneProgressUseCase;
        this.listMilestoneProgressUseCase = listMilestoneProgressUseCase;
    }

    /**
     * Returns project-level progress with aggregated task and milestone counts.
     */
    @Operation(summary = "Get project progress",
            description = "Returns aggregated task counts, milestone counts, and completion rate "
                    + "for a project. The completion rate is based on task-level aggregation "
                    + "across all milestones. The authenticated user must have an ACTIVE membership "
                    + "in the workspace. ARCHIVED projects are readable.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project progress"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/progress")
    public ResponseEntity<ApiResponse<ProjectProgressResponse>> getProjectProgress(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId) {

        ProjectProgressResult result = getProjectProgressUseCase.getProjectProgress(
                workspaceId, projectId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toProjectResponse(result), resolveRequestId()));
    }

    /**
     * Returns milestone-level progress with task counts.
     */
    @Operation(summary = "Get milestone progress",
            description = "Returns task counts and completion rate for a specific milestone. "
                    + "The milestone's own status is included but does not affect the calculation. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "ARCHIVED projects and COMPLETED milestones are readable.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone progress"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/progress")
    public ResponseEntity<ApiResponse<MilestoneProgressResponse>> getMilestoneProgress(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId) {

        MilestoneProgressResult result = getMilestoneProgressUseCase.getMilestoneProgress(
                workspaceId, projectId, milestoneId,
                principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toMilestoneResponse(result), resolveRequestId()));
    }

    /**
     * Returns progress for all milestones in a project.
     */
    @Operation(summary = "List milestone progress",
            description = "Returns progress entries for all milestones in a project, each with "
                    + "independent task counts and completion rates. "
                    + "Milestones with zero tasks show 0.00% completion. "
                    + "Ordered by due date (nulls last), then creation time. "
                    + "The authenticated user must have an ACTIVE membership in the workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone progress list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/milestones/progress")
    public ResponseEntity<ApiResponse<MilestoneProgressListResponse>> listMilestoneProgress(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId) {

        var results = listMilestoneProgressUseCase.listMilestoneProgress(
                workspaceId, projectId, principal.userId(), resolveRequestId());

        var items = results.stream()
                .map(ProgressController::toMilestoneResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.of(new MilestoneProgressListResponse(items), resolveRequestId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static ProjectProgressResponse toProjectResponse(ProjectProgressResult r) {
        return new ProjectProgressResponse(
                r.workspaceId().toString(),
                r.projectId().toString(),
                r.totalTasks(),
                r.completedTasks(),
                r.openTasks(),
                r.completionRate(),
                r.totalMilestones(),
                r.completedMilestones(),
                r.openMilestones()
        );
    }

    private static MilestoneProgressResponse toMilestoneResponse(MilestoneProgressResult r) {
        return new MilestoneProgressResponse(
                r.workspaceId().toString(),
                r.projectId().toString(),
                r.milestoneId().toString(),
                r.milestoneTitle(),
                r.milestoneStatus(),
                r.totalTasks(),
                r.completedTasks(),
                r.openTasks(),
                r.completionRate()
        );
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
