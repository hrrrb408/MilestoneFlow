package com.milestoneflow.task.api;

import com.milestoneflow.milestone.domain.exception.MilestoneCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.web.GlobalExceptionHandler;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.exception.TaskInvalidStatusException;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Task-module-specific exception handler.
 *
 * <p>Handles exceptions from the task domain module and maps them to
 * the unified {@link ApiErrorResponse} format.
 *
 * <p>Uses {@link Ordered#HIGHEST_PRECEDENCE} to ensure task-specific
 * handlers are resolved before the inherited catch-all from
 * {@link GlobalExceptionHandler}.
 *
 * <p>Cross-workspace, cross-project, cross-milestone, and non-member access
 * all return 404 to prevent resource existence leakage.
 */
@RestControllerAdvice(basePackages = "com.milestoneflow.task")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaskExceptionHandler extends GlobalExceptionHandler {

    public TaskExceptionHandler(java.time.Clock clock) {
        super(clock);
    }

    /**
     * Handles task not found or cross-workspace/project/milestone access.
     * Returns 404 TASK_NOT_FOUND.
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTaskNotFound(
            TaskNotFoundException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "TASK_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles milestone not found or cross-project/workspace access.
     * Returns 404 MILESTONE_NOT_FOUND.
     */
    @ExceptionHandler(MilestoneNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMilestoneNotFound(
            MilestoneNotFoundException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "MILESTONE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles project not found or cross-workspace access.
     * Returns 404 PROJECT_NOT_FOUND.
     */
    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectNotFound(
            ProjectNotFoundException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles workspace access denied for task operations.
     * Returns 404 WORKSPACE_NOT_FOUND (not 403) to prevent leakage.
     */
    @ExceptionHandler(WorkspaceAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceAccessDenied(
            WorkspaceAccessDeniedException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_NOT_FOUND",
                "Workspace not found",
                request.getRequestURI()
        );
    }

    /**
     * Handles attempt to create/update task in an archived project.
     * Returns 409 PROJECT_ARCHIVED.
     */
    @ExceptionHandler(ProjectArchivedException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectArchived(
            ProjectArchivedException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "PROJECT_ARCHIVED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles attempt to create/update task in a completed milestone.
     * Returns 409 MILESTONE_COMPLETED.
     */
    @ExceptionHandler(MilestoneCompletedException.class)
    public ResponseEntity<ApiErrorResponse> handleMilestoneCompleted(
            MilestoneCompletedException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "MILESTONE_COMPLETED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles non-OWNER attempting OWNER-only task operations.
     * Returns 403 WORKSPACE_OWNER_REQUIRED.
     */
    @ExceptionHandler(WorkspaceOwnerRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleOwnerRequired(
            WorkspaceOwnerRequiredException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                "WORKSPACE_OWNER_REQUIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles invalid task status value in query parameters.
     * Returns 422 TASK_INVALID_STATUS.
     */
    @ExceptionHandler(TaskInvalidStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStatus(
            TaskInvalidStatusException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TASK_INVALID_STATUS",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles invalid task priority value in request body or query parameters.
     * Returns 422 TASK_INVALID_PRIORITY.
     */
    @ExceptionHandler(TaskInvalidPriorityException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPriority(
            TaskInvalidPriorityException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TASK_INVALID_PRIORITY",
                ex.getMessage(),
                request.getRequestURI()
        );
    }
}
