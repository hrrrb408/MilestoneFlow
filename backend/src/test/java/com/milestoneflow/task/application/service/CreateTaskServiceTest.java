package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.task.application.command.CreateTaskCommand;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
 * Unit tests for CreateTaskService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateTaskService")
class CreateTaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock private TaskAuditWriter auditWriter;
    @Mock private IdGenerator idGenerator;

    private CreateTaskService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new CreateTaskService(taskRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker, auditWriter, idGenerator);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create task successfully")
        void shouldCreateTask() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(idGenerator.nextId()).thenReturn(TASK_ID);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
                Task t = inv.getArgument(0);
                return t;
            });

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "description", "HIGH",
                    LocalDate.of(2026, 7, 1));

            TaskResult result = service.create(command, USER_ID, REQUEST_ID);

            assertThat(result.taskId()).isEqualTo(TASK_ID);
            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.milestoneId()).isEqualTo(MILESTONE_ID);
            assertThat(result.title()).isEqualTo("Test Task");
            assertThat(result.description()).isEqualTo("description");
            assertThat(result.status()).isEqualTo("OPEN");
            assertThat(result.priority()).isEqualTo("HIGH");
            assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 7, 1));

            verify(auditWriter).writeUserEvent(
                    eq("TASK_CREATED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(TASK_ID), eq(REQUEST_ID), eq("Task created"),
                    any(Map.class));
        }

        @Test
        @DisplayName("should reject when not active member")
        void shouldRejectWhenNotActiveMember() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "MEDIUM", null);

            assertThatThrownBy(() -> service.create(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }

        @Test
        @DisplayName("should reject when project not found")
        void shouldRejectWhenProjectNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "MEDIUM", null);

            assertThatThrownBy(() -> service.create(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("should reject when project is archived")
        void shouldRejectWhenProjectIsArchived() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            Project archivedProject = createProject();
            archivedProject.archive(USER_ID, Instant.now());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(archivedProject));

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "MEDIUM", null);

            assertThatThrownBy(() -> service.create(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectArchivedException.class);
        }

        @Test
        @DisplayName("should reject when milestone not found")
        void shouldRejectWhenMilestoneNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.empty());

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "MEDIUM", null);

            assertThatThrownBy(() -> service.create(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneNotFoundException.class);
        }

        @Test
        @DisplayName("should reject when milestone is completed")
        void shouldRejectWhenMilestoneIsCompleted() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            Milestone completedMilestone = createMilestone();
            completedMilestone.complete(USER_ID, Instant.now());
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(completedMilestone));

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "MEDIUM", null);

            assertThatThrownBy(() -> service.create(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneCompletedException.class);
        }

        @Test
        @DisplayName("should default priority to MEDIUM when not specified")
        void shouldDefaultPriorityToMediumWhenNotSpecified() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(idGenerator.nextId()).thenReturn(TASK_ID);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", null, null);

            TaskResult result = service.create(command, USER_ID, REQUEST_ID);

            assertThat(result.priority()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("should reject invalid priority")
        void shouldRejectInvalidPriority() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "INVALID", null);

            assertThatThrownBy(() -> service.create(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskInvalidPriorityException.class);
        }

        @Test
        @DisplayName("should call audit writer with TASK_CREATED")
        void shouldCallAuditWriterWithTaskCreated() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(idGenerator.nextId()).thenReturn(TASK_ID);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTaskCommand command = new CreateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    "Test Task", "desc", "MEDIUM", null);

            service.create(command, USER_ID, REQUEST_ID);

            ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(auditWriter).writeUserEvent(
                    eq("TASK_CREATED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(TASK_ID), eq(REQUEST_ID), eq("Task created"),
                    metadataCaptor.capture());

            Map<String, Object> metadata = metadataCaptor.getValue();
            assertThat(metadata).containsEntry("title", "Test Task");
            assertThat(metadata).containsEntry("priority", "MEDIUM");
        }
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private WorkspaceMembership createMembership() {
        return WorkspaceMembership.createOwner(
                UUID.randomUUID(), WORKSPACE_ID, USER_ID, Instant.now());
    }

    private Project createProject() {
        return Project.create(PROJECT_ID, WORKSPACE_ID, "Test Project", "description",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
    }

    private Milestone createMilestone() {
        return Milestone.create(MILESTONE_ID, WORKSPACE_ID, PROJECT_ID,
                "Test Milestone", "description", LocalDate.of(2026, 7, 1));
    }
}
