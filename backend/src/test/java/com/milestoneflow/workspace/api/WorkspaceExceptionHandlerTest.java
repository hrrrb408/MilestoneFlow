package com.milestoneflow.workspace.api;

import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceAlreadyExistsForUserException;
import com.milestoneflow.workspace.domain.exception.WorkspaceNotFoundException;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
import com.milestoneflow.workspace.domain.exception.WorkspaceSlugAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkspaceExceptionHandler}.
 */
class WorkspaceExceptionHandlerTest {

    private WorkspaceExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new WorkspaceExceptionHandler(Clock.fixed(
                java.time.Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces/test");
    }

    // ── WorkspaceNotFoundException ───────────────────────────────────────

    @Nested
    @DisplayName("WorkspaceNotFoundException")
    class NotFound {

        @Test
        @DisplayName("should return 404 WORKSPACE_NOT_FOUND")
        void shouldReturn404() {
            var response = handler.handleWorkspaceNotFound(new WorkspaceNotFoundException(), request);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody().code()).isEqualTo("WORKSPACE_NOT_FOUND");
        }
    }

    // ── WorkspaceAccessDeniedException ───────────────────────────────────

    @Nested
    @DisplayName("WorkspaceAccessDeniedException")
    class AccessDenied {

        @Test
        @DisplayName("should return 404 WORKSPACE_NOT_FOUND (not 403) to prevent leakage")
        void shouldReturn404Not403() {
            var response = handler.handleWorkspaceAccessDenied(new WorkspaceAccessDeniedException(), request);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody().code()).isEqualTo("WORKSPACE_NOT_FOUND");
        }
    }

    // ── WorkspaceSlugAlreadyExistsException ──────────────────────────────

    @Nested
    @DisplayName("WorkspaceSlugAlreadyExistsException")
    class SlugConflict {

        @Test
        @DisplayName("should return 409 WORKSPACE_SLUG_ALREADY_EXISTS")
        void shouldReturn409() {
            var response = handler.handleSlugConflict(new WorkspaceSlugAlreadyExistsException(), request);

            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().code()).isEqualTo("WORKSPACE_SLUG_ALREADY_EXISTS");
        }
    }

    // ── WorkspaceAlreadyExistsForUserException ───────────────────────────

    @Nested
    @DisplayName("WorkspaceAlreadyExistsForUserException")
    class AlreadyExistsForUser {

        @Test
        @DisplayName("should return 409 WORKSPACE_ALREADY_EXISTS_FOR_USER")
        void shouldReturn409() {
            var response = handler.handleAlreadyExistsForUser(new WorkspaceAlreadyExistsForUserException(), request);

            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().code()).isEqualTo("WORKSPACE_ALREADY_EXISTS_FOR_USER");
        }
    }

    // ── WorkspaceOwnerRequiredException ──────────────────────────────────

    @Nested
    @DisplayName("WorkspaceOwnerRequiredException")
    class OwnerRequired {

        @Test
        @DisplayName("should return 403 WORKSPACE_OWNER_REQUIRED")
        void shouldReturn403() {
            var response = handler.handleOwnerRequired(new WorkspaceOwnerRequiredException(), request);

            assertThat(response.getStatusCode().value()).isEqualTo(403);
            assertThat(response.getBody().code()).isEqualTo("WORKSPACE_OWNER_REQUIRED");
        }
    }
}
