package com.milestoneflow.project.api;

import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectInvalidDateRangeException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProjectExceptionHandler.
 */
@DisplayName("ProjectExceptionHandler")
class ProjectExceptionHandlerTest {

    private ProjectExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ProjectExceptionHandler(Clock.systemUTC());
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces/ws-id/projects/p-id");
    }

    @Test
    @DisplayName("should map ProjectNotFoundException to 404")
    void shouldMapProjectNotFound() {
        ResponseEntity<ApiErrorResponse> response = handler.handleProjectNotFound(
                new ProjectNotFoundException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("PROJECT_NOT_FOUND");
    }

    @Test
    @DisplayName("should map WorkspaceAccessDeniedException to 404")
    void shouldMapWorkspaceAccessDenied() {
        ResponseEntity<ApiErrorResponse> response = handler.handleWorkspaceAccessDenied(
                new WorkspaceAccessDeniedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    @DisplayName("should map WorkspaceOwnerRequiredException to 403")
    void shouldMapOwnerRequired() {
        ResponseEntity<ApiErrorResponse> response = handler.handleOwnerRequired(
                new WorkspaceOwnerRequiredException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("WORKSPACE_OWNER_REQUIRED");
    }

    @Test
    @DisplayName("should map ProjectArchivedException to 409")
    void shouldMapProjectArchived() {
        ResponseEntity<ApiErrorResponse> response = handler.handleProjectArchived(
                new ProjectArchivedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("PROJECT_ARCHIVED");
    }

    @Test
    @DisplayName("should map ProjectInvalidDateRangeException to 422")
    void shouldMapInvalidDateRange() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidDateRange(
                new ProjectInvalidDateRangeException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().code()).isEqualTo("PROJECT_INVALID_DATE_RANGE");
    }
}
