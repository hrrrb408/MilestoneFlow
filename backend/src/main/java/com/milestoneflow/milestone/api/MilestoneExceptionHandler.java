package com.milestoneflow.milestone.api;

import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
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
 * Milestone-module-specific exception handler.
 *
 * <p>Handles exceptions from the milestone domain module and maps them to
 * the unified {@link ApiErrorResponse} format.
 *
 * <p>Uses {@link Ordered#HIGHEST_PRECEDENCE} to ensure milestone-specific
 * handlers are resolved before the inherited catch-all from
 * {@link GlobalExceptionHandler}.
 *
 * <p>Cross-workspace, cross-project, and non-member access all return 404
 * to prevent resource existence leakage.
 */
@RestControllerAdvice(basePackages = "com.milestoneflow.milestone")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MilestoneExceptionHandler extends GlobalExceptionHandler {

    public MilestoneExceptionHandler(java.time.Clock clock) {
        super(clock);
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
     * Handles workspace access denied for milestone operations.
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
     * Handles attempt to create/update milestone in an archived project.
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
     * Handles non-OWNER attempting OWNER-only milestone operations.
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
}
