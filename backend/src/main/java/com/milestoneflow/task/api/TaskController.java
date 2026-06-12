package com.milestoneflow.task.api;

import com.milestoneflow.shared.api.ApiResponse;
import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import com.milestoneflow.task.api.dto.CreateTaskRequest;
import com.milestoneflow.task.api.dto.TaskListResponse;
import com.milestoneflow.task.api.dto.TaskResponse;
import com.milestoneflow.task.api.dto.UpdateTaskRequest;
import com.milestoneflow.task.application.command.CreateTaskCommand;
import com.milestoneflow.task.application.command.UpdateTaskCommand;
import com.milestoneflow.task.application.port.in.CreateTaskUseCase;
import com.milestoneflow.task.application.port.in.GetTaskUseCase;
import com.milestoneflow.task.application.port.in.ListTasksUseCase;
import com.milestoneflow.task.application.port.in.UpdateTaskUseCase;
import com.milestoneflow.task.application.result.TaskResult;
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
 * REST controller for milestone-scoped task operations.
 *
 * <p>All endpoints require authentication via MF_ACCESS cookie.
 * Task endpoints are nested under milestone:
 * {@code /workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks}.
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}/tasks")
@Tag(name = "Tasks", description = "Milestone-scoped task management APIs")
@SecurityRequirement(name = "cookieAuth")
public class TaskController {

    private final CreateTaskUseCase createTaskUseCase;
    private final ListTasksUseCase listTasksUseCase;
    private final GetTaskUseCase getTaskUseCase;
    private final UpdateTaskUseCase updateTaskUseCase;

    public TaskController(CreateTaskUseCase createTaskUseCase,
                          ListTasksUseCase listTasksUseCase,
                          GetTaskUseCase getTaskUseCase,
                          UpdateTaskUseCase updateTaskUseCase) {
        this.createTaskUseCase = createTaskUseCase;
        this.listTasksUseCase = listTasksUseCase;
        this.getTaskUseCase = getTaskUseCase;
        this.updateTaskUseCase = updateTaskUseCase;
    }

    /**
     * Creates a new task in the specified milestone.
     */
    @Operation(summary = "Create a task",
            description = "Creates a new task in the specified milestone. "
                    + "The authenticated user must be the workspace OWNER. "
                    + "The project must not be archived. "
                    + "The milestone must not be completed. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Task created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace, project, or milestone not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is archived or milestone is completed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody CreateTaskRequest request) {

        CreateTaskCommand command = new CreateTaskCommand(
                workspaceId,
                projectId,
                milestoneId,
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate()
        );

        TaskResult result = createTaskUseCase.create(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Lists tasks in the specified milestone with optional filtering.
     */
    @Operation(summary = "List tasks",
            description = "Returns tasks in the specified milestone. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "By default, all tasks are returned ordered by status, priority, due date. "
                    + "Use status=OPEN or status=COMPLETED to filter by status. "
                    + "Use priority=LOW/MEDIUM/HIGH to filter by priority.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Task list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace, project, or milestone not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<TaskListResponse>> list(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @Parameter(description = "Filter by status (OPEN or COMPLETED)")
            @RequestParam(value = "status", required = false)
            String status,
            @Parameter(description = "Filter by priority (LOW, MEDIUM, or HIGH)")
            @RequestParam(value = "priority", required = false)
            String priority) {

        var results = listTasksUseCase.listTasks(
                workspaceId, projectId, milestoneId,
                principal.userId(), resolveRequestId(),
                status, priority);

        var items = results.stream()
                .map(TaskController::toResponse)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.of(new TaskListResponse(items), resolveRequestId()));
    }

    /**
     * Returns task details.
     */
    @Operation(summary = "Get task details",
            description = "Returns details of a specific task. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "Returns 404 if the task does not exist or does not belong to the "
                    + "milestone/project/workspace.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Task details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Task, milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> get(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @PathVariable UUID taskId) {

        TaskResult result = getTaskUseCase.getTask(
                workspaceId, projectId, milestoneId, taskId,
                principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Updates basic task information.
     */
    @Operation(summary = "Update task",
            description = "Updates task title, description, priority, and/or due date. "
                    + "Only the workspace OWNER can update tasks. "
                    + "Archived projects cannot have tasks updated. "
                    + "Completed milestones cannot have tasks updated. "
                    + "Requires CSRF token via X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Task updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not workspace owner",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Task, milestone, project, or workspace not found",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Project is archived or milestone is completed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = com.milestoneflow.shared.api.ApiErrorResponse.class)))
    })
    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {

        UpdateTaskCommand command = new UpdateTaskCommand(
                workspaceId,
                projectId,
                milestoneId,
                taskId,
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate()
        );

        TaskResult result = updateTaskUseCase.update(
                command, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static TaskResponse toResponse(TaskResult result) {
        return new TaskResponse(
                result.taskId().toString(),
                result.workspaceId().toString(),
                result.projectId().toString(),
                result.milestoneId().toString(),
                result.title(),
                result.description(),
                result.status(),
                result.priority(),
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
