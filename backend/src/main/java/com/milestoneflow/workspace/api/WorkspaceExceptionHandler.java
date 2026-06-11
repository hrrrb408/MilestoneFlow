package com.milestoneflow.workspace.api;

import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.web.GlobalExceptionHandler;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceAlreadyExistsForUserException;
import com.milestoneflow.workspace.domain.exception.WorkspaceNotFoundException;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
import com.milestoneflow.workspace.domain.exception.WorkspaceSlugAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Workspace-module-specific exception handler.
 *
 * <p>Handles exceptions from the workspace domain module and maps them to
 * the unified {@link ApiErrorResponse} format. Placed in the workspace.api
 * layer to respect ARCH-001 (shared must not depend on business modules).
 *
 * <p>Delegates response building to the shared {@link GlobalExceptionHandler}
 * for consistent formatting.
 */
@RestControllerAdvice
public class WorkspaceExceptionHandler extends GlobalExceptionHandler {

    public WorkspaceExceptionHandler(java.time.Clock clock) {
        super(clock);
    }

    /**
     * Handles workspace not found or access denied.
     * Returns 404 WORKSPACE_NOT_FOUND.
     *
     * <p>Same response for both cases to prevent workspace existence leakage.
     */
    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceNotFound(
            WorkspaceNotFoundException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles workspace access denied (non-member).
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
     * Handles slug conflict during workspace creation.
     * Returns 409 WORKSPACE_SLUG_ALREADY_EXISTS.
     */
    @ExceptionHandler(WorkspaceSlugAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleSlugConflict(
            WorkspaceSlugAlreadyExistsException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "WORKSPACE_SLUG_ALREADY_EXISTS",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles user already having an active workspace.
     * Returns 409 WORKSPACE_ALREADY_EXISTS_FOR_USER.
     */
    @ExceptionHandler(WorkspaceAlreadyExistsForUserException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExistsForUser(
            WorkspaceAlreadyExistsForUserException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "WORKSPACE_ALREADY_EXISTS_FOR_USER",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    /**
     * Handles non-OWNER attempting OWNER-only operations.
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
     * Safety-net handler for database unique constraint violations that escape
     * the repository adapter (e.g., JPA deferred flush at commit time).
     *
     * <p>Maps workspace-related constraints to business exceptions.
     * Other data integrity errors are re-thrown for the global 500 handler.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        String constraintName = extractConstraintName(ex);

        if ("uk_workspace_slug".equals(constraintName)) {
            return build(
                    HttpStatus.CONFLICT,
                    "WORKSPACE_SLUG_ALREADY_EXISTS",
                    "Workspace slug is already taken",
                    request.getRequestURI()
            );
        }

        if ("uk_workspace_membership_active_user".equals(constraintName)) {
            return build(
                    HttpStatus.CONFLICT,
                    "WORKSPACE_ALREADY_EXISTS_FOR_USER",
                    "User already has an active workspace",
                    request.getRequestURI()
            );
        }

        if ("uk_workspace_membership".equals(constraintName)) {
            return build(
                    HttpStatus.CONFLICT,
                    "WORKSPACE_ALREADY_EXISTS_FOR_USER",
                    "User already has an active workspace",
                    request.getRequestURI()
            );
        }

        // Other data integrity violations are not ours to handle.
        throw ex;
    }

    private static String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current.getClass().getName()
                    .equals("org.hibernate.exception.ConstraintViolationException")) {
                try {
                    var method = current.getClass().getMethod("getConstraintName");
                    return (String) method.invoke(current);
                } catch (Exception ignored) {
                    // Reflection failed
                }
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }
}
