package com.milestoneflow.project.api;

import com.milestoneflow.project.api.dto.CreateProjectRequest;
import com.milestoneflow.project.api.dto.ProjectListResponse;
import com.milestoneflow.project.api.dto.ProjectResponse;
import com.milestoneflow.project.api.dto.UpdateProjectRequest;
import com.milestoneflow.project.application.command.CreateProjectCommand;
import com.milestoneflow.project.application.command.UpdateProjectCommand;
import com.milestoneflow.project.application.port.in.ArchiveProjectUseCase;
import com.milestoneflow.project.application.port.in.CreateProjectUseCase;
import com.milestoneflow.project.application.port.in.GetProjectUseCase;
import com.milestoneflow.project.application.port.in.ListProjectsUseCase;
import com.milestoneflow.project.application.port.in.RestoreProjectUseCase;
import com.milestoneflow.project.application.port.in.UpdateProjectUseCase;
import com.milestoneflow.project.application.result.ProjectResult;
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
 * REST controller for workspace-scoped project operations.
 *
 * <p>All endpoints require authentication via MF_ACCESS cookie.
 * Project endpoints are nested under workspace: {@code /workspaces/{workspaceId}/projects}.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/projects")
@Tag(name = "Projects", description = "Workspace-scoped project management APIs")
@SecurityRequirement(name = "cookieAuth")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final ListProjectsUseCase listProjectsUseCase;
    private final GetProjectUseCase getProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final ArchiveProjectUseCase archiveProjectUseCase;
    private final RestoreProjectUseCase restoreProjectUseCase;

    public ProjectController(CreateProjectUseCase createProjectUseCase,
                             ListProjectsUseCase listProjectsUseCase,
                             GetProjectUseCase getProjectUseCase,
                             UpdateProjectUseCase updateProjectUseCase,
                             ArchiveProjectUseCase archiveProjectUseCase,
                             RestoreProjectUseCase restoreProjectUseCase) {
        this.createProjectUseCase = createProjectUseCase;
        this.listProjectsUseCase = listProjectsUseCase;
        this.getProjectUseCase = getProjectUseCase;
        this.updateProjectUseCase = updateProjectUseCase;
        this.archiveProjectUseCase = archiveProjectUseCase;
        this.restoreProjectUseCase = restoreProjectUseCase;
    }

    /**
     * Creates a new project in the specified workspace.
     */
    @Operation(summary = "Create a project",
            description = "Creates a new project in the specified workspace. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Project created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProjectRequest request) {

        CreateProjectCommand command = new CreateProjectCommand(
                workspaceId,
                request.name(),
                request.description(),
                request.startDate(),
                request.targetDate()
        );

        ProjectResult result = createProjectUseCase.create(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Lists projects in the specified workspace with optional filtering.
     */
    @Operation(summary = "List projects",
            description = "Returns projects in the specified workspace. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "By default, only ACTIVE projects are returned. "
                    + "Use includeArchived=true to include archived projects, "
                    + "or status=ARCHIVED to list only archived projects.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<ProjectListResponse>> list(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @Parameter(description = "Include archived projects (default: false)")
            @RequestParam(value = "includeArchived", required = false, defaultValue = "false")
            Boolean includeArchived,
            @Parameter(description = "Filter by specific status (ACTIVE or ARCHIVED). Takes priority over includeArchived.")
            @RequestParam(value = "status", required = false)
            String status) {

        var results = listProjectsUseCase.listProjects(
                workspaceId, principal.userId(), resolveRequestId(),
                includeArchived, status);

        var items = results.stream()
                .map(ProjectController::toResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.of(new ProjectListResponse(items), resolveRequestId()));
    }

    /**
     * Returns project details.
     */
    @Operation(summary = "Get project details",
            description = "Returns details of a specific project. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "Returns 404 if the project does not exist or does not belong to the workspace. "
                    + "ARCHIVED projects can still be viewed by active members.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId) {

        ProjectResult result = getProjectUseCase.getProject(
                workspaceId, projectId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Updates basic project information.
     */
    @Operation(summary = "Update project",
            description = "Updates project name, description, and/or dates. "
                    + "Only the workspace OWNER can update projects. "
                    + "Archived projects cannot be updated. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PatchMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {

        UpdateProjectCommand command = new UpdateProjectCommand(
                workspaceId,
                projectId,
                request.name(),
                request.description(),
                request.startDate(),
                request.targetDate()
        );

        ProjectResult result = updateProjectUseCase.update(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Archives a project.
     */
    @Operation(summary = "Archive project",
            description = "Archives an ACTIVE project. Only workspace OWNER can archive. "
                    + "Archived projects cannot be updated but can still be viewed. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project archived successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is already archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping("/{projectId}/archive")
    public ResponseEntity<ApiResponse<ProjectResponse>> archive(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId) {

        ProjectResult result = archiveProjectUseCase.archive(
                workspaceId, projectId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Restores an archived project.
     */
    @Operation(summary = "Restore project",
            description = "Restores an ARCHIVED project back to ACTIVE. Only workspace OWNER can restore. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Project restored successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Project not found or access denied",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is not archived",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping("/{projectId}/restore")
    public ResponseEntity<ApiResponse<ProjectResponse>> restore(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId) {

        ProjectResult result = restoreProjectUseCase.restore(
                workspaceId, projectId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static ProjectResponse toResponse(ProjectResult result) {
        return new ProjectResponse(
                result.projectId().toString(),
                result.workspaceId().toString(),
                result.name(),
                result.description(),
                result.status(),
                result.startDate(),
                result.targetDate(),
                result.archivedAt() != null ? result.archivedAt().toString() : null,
                result.createdAt() != null ? result.createdAt().toString() : null,
                result.updatedAt() != null ? result.updatedAt().toString() : null
        );
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
