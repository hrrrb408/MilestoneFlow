package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.command.CreateProjectCommand;
import com.milestoneflow.project.application.port.out.ProjectAuditWriter;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.exception.ProjectInvalidDateRangeException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CreateProjectService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateProjectService")
class CreateProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock private ProjectAuditWriter auditWriter;
    @Mock private IdGenerator idGenerator;

    private CreateProjectService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CreateProjectService(projectRepository, workspaceAccessChecker,
                auditWriter, idGenerator);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create project successfully")
        void shouldCreateProject() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(idGenerator.nextId()).thenReturn(PROJECT_ID);
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
                Project p = inv.getArgument(0);
                return p;
            });

            CreateProjectCommand command = new CreateProjectCommand(
                    WORKSPACE_ID, "Test Project", "desc",
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));

            ProjectResult result = service.create(command, USER_ID, "req-123");

            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.name()).isEqualTo("Test Project");
            assertThat(result.status()).isEqualTo("ACTIVE");
            verify(auditWriter).writeUserEvent(
                    eq("PROJECT_CREATED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(PROJECT_ID), eq("req-123"), eq("Project created"),
                    any(Map.class));
        }

        @Test
        @DisplayName("should reject invalid date range")
        void shouldRejectInvalidDateRange() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());

            CreateProjectCommand command = new CreateProjectCommand(
                    WORKSPACE_ID, "Test", null,
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1));

            assertThatThrownBy(() -> service.create(command, USER_ID, "req"))
                    .isInstanceOf(ProjectInvalidDateRangeException.class);
        }
    }

    private WorkspaceMembership createMembership() {
        return WorkspaceMembership.createOwner(
                UUID.randomUUID(), WORKSPACE_ID, USER_ID, Instant.now());
    }
}
