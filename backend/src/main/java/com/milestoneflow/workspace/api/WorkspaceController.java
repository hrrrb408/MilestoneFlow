package com.milestoneflow.workspace.api;

import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import com.milestoneflow.shared.api.ApiResponse;
import com.milestoneflow.workspace.api.dto.CreateWorkspaceRequest;
import com.milestoneflow.workspace.api.dto.UpdateWorkspaceRequest;
import com.milestoneflow.workspace.api.dto.WorkspaceResponse;
import com.milestoneflow.workspace.application.command.CreateWorkspaceCommand;
import com.milestoneflow.workspace.application.command.UpdateWorkspaceCommand;
import com.milestoneflow.workspace.application.port.in.CreateWorkspaceUseCase;
import com.milestoneflow.workspace.application.port.in.GetCurrentWorkspaceUseCase;
import com.milestoneflow.workspace.application.port.in.GetWorkspaceUseCase;
import com.milestoneflow.workspace.application.port.in.UpdateWorkspaceUseCase;
import com.milestoneflow.workspace.application.result.WorkspaceResult;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for workspace operations.
 *
 * <p>All endpoints require authentication via MF_ACCESS cookie.
 * Workspace endpoints are under {@code /workspaces} which resolves to
 * {@code /api/v1/workspaces} via the server servlet context path.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@RequestMapping("/workspaces")
@Tag(name = "Workspaces", description = "Workspace management APIs")
@SecurityRequirement(name = "cookieAuth")
public class WorkspaceController {

    private final CreateWorkspaceUseCase createWorkspaceUseCase;
    private final GetCurrentWorkspaceUseCase getCurrentWorkspaceUseCase;
    private final GetWorkspaceUseCase getWorkspaceUseCase;
    private final UpdateWorkspaceUseCase updateWorkspaceUseCase;

    public WorkspaceController(CreateWorkspaceUseCase createWorkspaceUseCase,
                               GetCurrentWorkspaceUseCase getCurrentWorkspaceUseCase,
                               GetWorkspaceUseCase getWorkspaceUseCase,
                               UpdateWorkspaceUseCase updateWorkspaceUseCase) {
        this.createWorkspaceUseCase = createWorkspaceUseCase;
        this.getCurrentWorkspaceUseCase = getCurrentWorkspaceUseCase;
        this.getWorkspaceUseCase = getWorkspaceUseCase;
        this.updateWorkspaceUseCase = updateWorkspaceUseCase;
    }

    /**
     * Creates a new workspace with the authenticated user as OWNER.
     */
    @Operation(summary = "Create a new workspace",
            description = "Creates a new workspace with the authenticated user as OWNER. "
                    + "Each user can only have one active workspace. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Workspace created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Slug already taken or user already has a workspace",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody CreateWorkspaceRequest request) {

        CreateWorkspaceCommand command = new CreateWorkspaceCommand(
                request.name(),
                request.slug(),
                request.timezone(),
                request.defaultCurrency()
        );

        WorkspaceResult result = createWorkspaceUseCase.create(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Returns the current user's active workspace.
     */
    @Operation(summary = "Get current workspace",
            description = "Returns the workspace where the authenticated user has an ACTIVE membership. "
                    + "Returns 404 if the user has no workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Current workspace found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "User has no active workspace",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> current(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {

        return getCurrentWorkspaceUseCase.getCurrentWorkspace(principal.userId())
                .map(result -> ResponseEntity.ok(
                        ApiResponse.of(toResponse(result), resolveRequestId())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.of(null, resolveRequestId())));
    }

    /**
     * Returns workspace details by ID.
     */
    @Operation(summary = "Get workspace details",
            description = "Returns details of a specific workspace. "
                    + "The authenticated user must have an ACTIVE membership in the workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Workspace details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> get(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId) {

        WorkspaceResult result = getWorkspaceUseCase.getWorkspace(workspaceId, principal.userId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Updates workspace basic information.
     */
    @Operation(summary = "Update workspace",
            description = "Updates workspace name, timezone, and/or default currency. "
                    + "Only the OWNER can update workspace info. "
                    + "Slug cannot be changed. Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Workspace updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PatchMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> update(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {

        UpdateWorkspaceCommand command = new UpdateWorkspaceCommand(
                workspaceId,
                request.name(),
                request.timezone(),
                request.defaultCurrency()
        );

        WorkspaceResult result = updateWorkspaceUseCase.update(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static WorkspaceResponse toResponse(WorkspaceResult result) {
        return new WorkspaceResponse(
                result.workspaceId().toString(),
                result.name(),
                result.slug(),
                result.status(),
                result.timezone(),
                result.defaultCurrency(),
                result.role(),
                result.createdAt().toString()
        );
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
