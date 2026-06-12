package com.milestoneflow.milestone.api;

import com.milestoneflow.milestone.api.dto.CreateMilestoneRequest;
import com.milestoneflow.milestone.api.dto.MilestoneListResponse;
import com.milestoneflow.milestone.api.dto.MilestoneResponse;
import com.milestoneflow.milestone.api.dto.UpdateMilestoneRequest;
import com.milestoneflow.milestone.application.command.CreateMilestoneCommand;
import com.milestoneflow.milestone.application.command.UpdateMilestoneCommand;
import com.milestoneflow.milestone.application.port.in.CompleteMilestoneUseCase;
import com.milestoneflow.milestone.application.port.in.CreateMilestoneUseCase;
import com.milestoneflow.milestone.application.port.in.GetMilestoneUseCase;
import com.milestoneflow.milestone.application.port.in.ListMilestonesUseCase;
import com.milestoneflow.milestone.application.port.in.ReopenMilestoneUseCase;
import com.milestoneflow.milestone.application.port.in.UpdateMilestoneUseCase;
import com.milestoneflow.milestone.application.result.MilestoneResult;
import com.milestoneflow.shared.api.ApiResponse;
import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for project-scoped milestone operations.
 *
 * <p>All endpoints require authentication via MF_ACCESS cookie.
 * Milestone endpoints are nested under project:
 * {@code /workspaces/{workspaceId}/projects/{projectId}/milestones}.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/projects/{projectId}/milestones")
@Tag(name = "Milestones", description = "Project-scoped milestone management APIs")
@SecurityRequirement(name = "cookieAuth")
public class MilestoneController {

    private final CreateMilestoneUseCase createMilestoneUseCase;
    private final ListMilestonesUseCase listMilestonesUseCase;
    private final GetMilestoneUseCase getMilestoneUseCase;
    private final UpdateMilestoneUseCase updateMilestoneUseCase;
    private final CompleteMilestoneUseCase completeMilestoneUseCase;
    private final ReopenMilestoneUseCase reopenMilestoneUseCase;

    public MilestoneController(CreateMilestoneUseCase createMilestoneUseCase,
                               ListMilestonesUseCase listMilestonesUseCase,
                               GetMilestoneUseCase getMilestoneUseCase,
                               UpdateMilestoneUseCase updateMilestoneUseCase,
                               CompleteMilestoneUseCase completeMilestoneUseCase,
                               ReopenMilestoneUseCase reopenMilestoneUseCase) {
        this.createMilestoneUseCase = createMilestoneUseCase;
        this.listMilestonesUseCase = listMilestonesUseCase;
        this.getMilestoneUseCase = getMilestoneUseCase;
        this.updateMilestoneUseCase = updateMilestoneUseCase;
        this.completeMilestoneUseCase = completeMilestoneUseCase;
        this.reopenMilestoneUseCase = reopenMilestoneUseCase;
    }

    /**
     * Creates a new milestone in the specified project.
     */
    @Operation(summary = "Create a milestone",
            description = "Creates a new milestone in the specified project. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "The project must not be archived. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Milestone created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace or project not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<MilestoneResponse>> create(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMilestoneRequest request) {

        CreateMilestoneCommand command = new CreateMilestoneCommand(
                workspaceId,
                projectId,
                request.title(),
                request.description(),
                request.dueDate()
        );

        MilestoneResult result = createMilestoneUseCase.create(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Lists milestones in the specified project with optional filtering.
     */
    @Operation(summary = "List milestones",
            description = "Returns milestones in the specified project. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "By default, all milestones are returned ordered by due date. "
                    + "Use status=OPEN or status=COMPLETED to filter by status.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace or project not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MilestoneListResponse>> list(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Parameter(description = "Filter by status (OPEN or COMPLETED)")
            @RequestParam(value = "status", required = false)
            String status) {

        var results = listMilestonesUseCase.listMilestones(
                workspaceId, projectId, principal.userId(), resolveRequestId(), status);

        var items = results.stream()
                .map(MilestoneController::toResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.of(new MilestoneListResponse(items), resolveRequestId()));
    }

    /**
     * Returns milestone details.
     */
    @Operation(summary = "Get milestone details",
            description = "Returns details of a specific milestone. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "Returns 404 if the milestone does not exist or does not belong to the project/workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> get(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId) {

        MilestoneResult result = getMilestoneUseCase.getMilestone(
                workspaceId, projectId, milestoneId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Updates basic milestone information.
     */
    @Operation(summary = "Update milestone",
            description = "Updates milestone title, description, and/or due date. "
                    + "Only the workspace OWNER can update milestones. "
                    + "Archived projects cannot have milestones updated. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PatchMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> update(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request) {

        UpdateMilestoneCommand command = new UpdateMilestoneCommand(
                workspaceId,
                projectId,
                milestoneId,
                request.title(),
                request.description(),
                request.dueDate()
        );

        MilestoneResult result = updateMilestoneUseCase.update(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Completes a milestone.
     */
    @Operation(summary = "Complete a milestone",
            description = "Transitions a milestone from OPEN to COMPLETED. "
                    + "The authenticated user must be the workspace OWNER. "
                    + "The project must not be archived. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone completed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Milestone already completed or project is archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping("/{milestoneId}/complete")
    public ResponseEntity<ApiResponse<MilestoneResponse>> complete(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId) {

        MilestoneResult result = completeMilestoneUseCase.complete(
                workspaceId, projectId, milestoneId,
                principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Reopens a completed milestone.
     */
    @Operation(summary = "Reopen a milestone",
            description = "Transitions a milestone from COMPLETED back to OPEN. "
                    + "The authenticated user must be the workspace OWNER. "
                    + "The project must not be archived. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Milestone reopened successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Milestone not completed or project is archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping("/{milestoneId}/reopen")
    public ResponseEntity<ApiResponse<MilestoneResponse>> reopen(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId) {

        MilestoneResult result = reopenMilestoneUseCase.reopen(
                workspaceId, projectId, milestoneId,
                principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static MilestoneResponse toResponse(MilestoneResult result) {
        return new MilestoneResponse(
                result.milestoneId().toString(),
                result.workspaceId().toString(),
                result.projectId().toString(),
                result.title(),
                result.description(),
                result.status(),
                result.dueDate(),
                result.completedAt() != null ? result.completedAt().toString() : null,
                result.createdAt() != null ? result.createdAt().toString() : null,
                result.updatedAt() != null ? result.updatedAt().toString() : null
        );
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
