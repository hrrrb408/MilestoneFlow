package com.milestoneflow.activity.api;

import com.milestoneflow.activity.api.dto.ActivityLogListResponse;
import com.milestoneflow.activity.api.dto.ActivityLogResponse;
import com.milestoneflow.activity.application.port.in.ListMilestoneActivitiesUseCase;
import com.milestoneflow.activity.application.port.in.ListProjectActivitiesUseCase;
import com.milestoneflow.activity.application.port.in.ListTaskActivitiesUseCase;
import com.milestoneflow.activity.application.port.in.ListWorkspaceActivitiesUseCase;
import com.milestoneflow.activity.application.result.ActivityLogRow;
import com.milestoneflow.shared.api.ApiResponse;
import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for activity log timeline queries.
 *
 * <p>All endpoints require authentication via MF_ACCESS cookie.
 * Activity log endpoints are read-only — no CSRF token required for GET.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@Tag(name = "Activity Log", description = "Activity timeline APIs")
@SecurityRequirement(name = "cookieAuth")
public class ActivityLogController {

    private final ListWorkspaceActivitiesUseCase listWorkspaceActivitiesUseCase;
    private final ListProjectActivitiesUseCase listProjectActivitiesUseCase;
    private final ListMilestoneActivitiesUseCase listMilestoneActivitiesUseCase;
    private final ListTaskActivitiesUseCase listTaskActivitiesUseCase;

    public ActivityLogController(ListWorkspaceActivitiesUseCase listWorkspaceActivitiesUseCase,
                                  ListProjectActivitiesUseCase listProjectActivitiesUseCase,
                                  ListMilestoneActivitiesUseCase listMilestoneActivitiesUseCase,
                                  ListTaskActivitiesUseCase listTaskActivitiesUseCase) {
        this.listWorkspaceActivitiesUseCase = listWorkspaceActivitiesUseCase;
        this.listProjectActivitiesUseCase = listProjectActivitiesUseCase;
        this.listMilestoneActivitiesUseCase = listMilestoneActivitiesUseCase;
        this.listTaskActivitiesUseCase = listTaskActivitiesUseCase;
    }

    // ── Workspace activities ─────────────────────────────────────────────────

    @Operation(summary = "List workspace activities",
            description = "Returns activity events for a workspace, ordered by createdAt DESC. "
                    + "Supports optional eventType and targetType filters. "
                    + "The authenticated user must have an ACTIVE membership in the workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Workspace activity list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/activities")
    public ResponseEntity<ApiResponse<ActivityLogListResponse>> listWorkspaceActivities(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @Parameter(description = "Maximum number of results (1–100, default 20)")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Filter by event type (e.g., TASK_CREATED)")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "Filter by target type (e.g., TASK, PROJECT)")
            @RequestParam(required = false) String targetType) {

        int effectiveLimit = limit != null ? limit : 20;

        List<ActivityLogRow> rows = listWorkspaceActivitiesUseCase.listWorkspaceActivities(
                workspaceId, principal.userId(), effectiveLimit,
                eventType, targetType, resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toListResponse(rows), resolveRequestId()));
    }

    // ── Project activities ───────────────────────────────────────────────────

    @Operation(summary = "List project activities",
            description = "Returns activity events for a specific project, ordered by createdAt DESC. "
                    + "Only project-level events are included (targetType = PROJECT). "
                    + "ARCHIVED projects are readable. "
                    + "The authenticated user must have an ACTIVE membership in the workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project activity list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/activities")
    public ResponseEntity<ApiResponse<ActivityLogListResponse>> listProjectActivities(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Parameter(description = "Maximum number of results (1–100, default 20)")
            @RequestParam(required = false) Integer limit) {

        int effectiveLimit = limit != null ? limit : 20;

        List<ActivityLogRow> rows = listProjectActivitiesUseCase.listProjectActivities(
                workspaceId, projectId, principal.userId(), effectiveLimit,
                resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toListResponse(rows), resolveRequestId()));
    }

    // ── Milestone activities ─────────────────────────────────────────────────

    @Operation(summary = "List milestone activities",
            description = "Returns activity events for a specific milestone, ordered by createdAt DESC. "
                    + "Only milestone-level events are included (targetType = MILESTONE). "
                    + "COMPLETED milestones are readable. "
                    + "The authenticated user must have an ACTIVE membership in the workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone activity list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/activities")
    public ResponseEntity<ApiResponse<ActivityLogListResponse>> listMilestoneActivities(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @Parameter(description = "Maximum number of results (1–100, default 20)")
            @RequestParam(required = false) Integer limit) {

        int effectiveLimit = limit != null ? limit : 20;

        List<ActivityLogRow> rows = listMilestoneActivitiesUseCase.listMilestoneActivities(
                workspaceId, projectId, milestoneId, principal.userId(),
                effectiveLimit, resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toListResponse(rows), resolveRequestId()));
    }

    // ── Task activities ──────────────────────────────────────────────────────

    @Operation(summary = "List task activities",
            description = "Returns activity events for a specific task, ordered by createdAt DESC. "
                    + "COMPLETED tasks are readable. "
                    + "The authenticated user must have an ACTIVE membership in the workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Task activity list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Task, milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks/{taskId}/activities")
    public ResponseEntity<ApiResponse<ActivityLogListResponse>> listTaskActivities(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @PathVariable UUID taskId,
            @Parameter(description = "Maximum number of results (1–100, default 20)")
            @RequestParam(required = false) Integer limit) {

        int effectiveLimit = limit != null ? limit : 20;

        List<ActivityLogRow> rows = listTaskActivitiesUseCase.listTaskActivities(
                workspaceId, projectId, milestoneId, taskId,
                principal.userId(), effectiveLimit, resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toListResponse(rows), resolveRequestId()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ActivityLogListResponse toListResponse(List<ActivityLogRow> rows) {
        List<ActivityLogResponse> items = rows.stream()
                .map(ActivityLogController::toResponse)
                .toList();
        return new ActivityLogListResponse(items, null);
    }

    private static ActivityLogResponse toResponse(ActivityLogRow row) {
        return new ActivityLogResponse(
                row.id().toString(),
                row.workspaceId() != null ? row.workspaceId().toString() : null,
                row.actorId() != null ? row.actorId().toString() : null,
                row.actorType(),
                row.eventType(),
                row.targetType(),
                row.targetId() != null ? row.targetId().toString() : null,
                row.summary(),
                row.metadata(),
                toOffsetDateTime(row.createdAt())
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
