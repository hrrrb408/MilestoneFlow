package com.milestoneflow.project.api;

import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectInvalidDateRangeException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.web.GlobalExceptionHandler;
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
 * Project-module-specific exception handler.
 *
 * <p>Handles exceptions from the project domain module and maps them to
 * the unified {@link ApiErrorResponse} format.
 *
 * <p>Uses {@link Ordered#HIGHEST_PRECEDENCE} to ensure project-specific
 * handlers are resolved before the inherited catch-all from
 * {@link GlobalExceptionHandler}.
 *
 * <p>Cross-workspace and non-member access both return 404 to prevent
 * resource existence leakage.
 */
@RestControllerAdvice(basePackages = "com.milestoneflow.project")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProjectExceptionHandler extends GlobalExceptionHandler {

    public ProjectExceptionHandler(java.time.Clock clock) {
        super(clock);
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
     * Handles workspace access denied for project operations.
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
     * Handles attempt to modify an archived project.
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
     * Handles non-OWNER attempting OWNER-only project operations.
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
     * Handles invalid date range (startDate > targetDate).
     * Returns 422 PROJECT_INVALID_DATE_RANGE.
     */
    @ExceptionHandler(ProjectInvalidDateRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDateRange(
            ProjectInvalidDateRangeException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PROJECT_INVALID_DATE_RANGE",
                ex.getMessage(),
                request.getRequestURI()
        );
    }
}
