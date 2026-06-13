package com.milestoneflow.progress.api;

import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.web.GlobalExceptionHandler;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Progress-module-specific exception handler.
 *
 * <p>Handles exceptions from the progress module and maps them to the unified
 * {@link ApiErrorResponse} format. Reuses domain exceptions from project, milestone,
 * and workspace modules.
 *
 * <p>Cross-workspace, cross-project, cross-milestone, and non-member access
 * all return 404 to prevent resource existence leakage.
 */
@RestControllerAdvice(basePackages = "com.milestoneflow.progress")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProgressExceptionHandler extends GlobalExceptionHandler {

    public ProgressExceptionHandler(java.time.Clock clock) {
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
     * Handles workspace access denied for progress queries.
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
}
