package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.port.out.ProjectAuditWriter;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ArchiveProjectService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArchiveProjectService")
class ArchiveProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock private ProjectAuditWriter auditWriter;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    private ArchiveProjectService service;

    @BeforeEach
    void setUp() {
        service = new ArchiveProjectService(projectRepository, workspaceAccessChecker,
                auditWriter, FIXED_CLOCK);
    }

    @Nested
    @DisplayName("archive")
    class Archive {

        @Test
        @DisplayName("should archive ACTIVE project successfully")
        void shouldArchiveActiveProject() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createOwnerMembership());
            Project project = Project.create(PROJECT_ID, WORKSPACE_ID, "Test", null, null, null);
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            ProjectResult result = service.archive(WORKSPACE_ID, PROJECT_ID, USER_ID, "req-123");

            assertThat(result.status()).isEqualTo("ARCHIVED");
            assertThat(result.archivedAt()).isEqualTo(Instant.parse("2026-06-12T00:00:00Z"));
            verify(auditWriter).writeUserEvent(
                    eq("PROJECT_ARCHIVED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(PROJECT_ID), eq("req-123"), eq("Project archived"),
                    any(Map.class));
        }

        @Test
        @DisplayName("should reject archive of already ARCHIVED project")
        void shouldRejectDoubleArchive() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createOwnerMembership());
            Project project = Project.create(PROJECT_ID, WORKSPACE_ID, "Test", null, null, null);
            project.archive(USER_ID, Instant.now());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(project));

            assertThatThrownBy(() -> service.archive(WORKSPACE_ID, PROJECT_ID, USER_ID, "req"))
                    .isInstanceOf(ProjectArchivedException.class);
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException for missing project")
        void shouldThrowNotFound() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createOwnerMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archive(WORKSPACE_ID, PROJECT_ID, USER_ID, "req"))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when user is not OWNER")
        void shouldRejectNonOwner() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceOwnerRequiredException());

            assertThatThrownBy(() -> service.archive(WORKSPACE_ID, PROJECT_ID, USER_ID, "req"))
                    .isInstanceOf(WorkspaceOwnerRequiredException.class);
        }

        @Test
        @DisplayName("should throw when user has no membership")
        void shouldRejectNonMember() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.archive(WORKSPACE_ID, PROJECT_ID, USER_ID, "req"))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }
    }

    private WorkspaceMembership createOwnerMembership() {
        return WorkspaceMembership.createOwner(
                UUID.randomUUID(), WORKSPACE_ID, USER_ID, Instant.now());
    }
}
