package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.task.application.command.UpdateTaskCommand;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskCompletedException;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
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
 * Unit tests for UpdateTaskService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateTaskService")
class UpdateTaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock private TaskAuditWriter auditWriter;

    private UpdateTaskService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new UpdateTaskService(taskRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker, auditWriter);
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update task successfully")
        void shouldUpdateTaskSuccessfully() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(createTask()));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", "Updated Description", "HIGH",
                    LocalDate.of(2026, 8, 1));

            TaskResult result = service.update(command, USER_ID, REQUEST_ID);

            assertThat(result.taskId()).isEqualTo(TASK_ID);
            assertThat(result.title()).isEqualTo("Updated Title");
            assertThat(result.description()).isEqualTo("Updated Description");
            assertThat(result.priority()).isEqualTo("HIGH");
            assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("should reject when not owner")
        void shouldRejectWhenNotOwner() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceOwnerRequiredException());

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceOwnerRequiredException.class);
        }

        @Test
        @DisplayName("should reject when project not found")
        void shouldRejectWhenProjectNotFound() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("should reject when project is archived")
        void shouldRejectWhenProjectIsArchived() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            Project archivedProject = createProject();
            archivedProject.archive(USER_ID, Instant.now());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(archivedProject));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectArchivedException.class);
        }

        @Test
        @DisplayName("should reject when milestone not found")
        void shouldRejectWhenMilestoneNotFound() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.empty());

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneNotFoundException.class);
        }

        @Test
        @DisplayName("should reject when milestone is completed")
        void shouldRejectWhenMilestoneIsCompleted() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            Milestone completedMilestone = createMilestone();
            completedMilestone.complete(USER_ID, Instant.now());
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(completedMilestone));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneCompletedException.class);
        }

        @Test
        @DisplayName("should throw TaskNotFoundException when task not found")
        void shouldThrowTaskNotFoundExceptionWhenTaskNotFound() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.empty());

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("should reject invalid priority")
        void shouldRejectInvalidPriority() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(createTask()));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Title", null, "INVALID", null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskInvalidPriorityException.class);
        }

        @Test
        @DisplayName("should reject update when task is COMPLETED")
        void shouldRejectUpdateWhenTaskIsCompleted() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            Task completedTask = createTask();
            completedTask.complete(USER_ID, Instant.now());
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(completedTask));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            assertThatThrownBy(() -> service.update(command, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskCompletedException.class);
        }

        @Test
        @DisplayName("should allow update after reopen")
        void shouldAllowUpdateAfterReopen() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            Task task = createTask();
            task.complete(USER_ID, Instant.now());
            task.reopen();
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(task));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", null, null, null);

            TaskResult result = service.update(command, USER_ID, REQUEST_ID);
            assertThat(result.title()).isEqualTo("Updated Title");
        }

        @Test
        @DisplayName("should call audit writer with TASK_UPDATED")
        void shouldCallAuditWriterWithTaskUpdated() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(createTask()));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTaskCommand command = new UpdateTaskCommand(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    "Updated Title", "Updated Desc", "HIGH",
                    LocalDate.of(2026, 8, 1));

            service.update(command, USER_ID, REQUEST_ID);

            ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(auditWriter).writeUserEvent(
                    eq("TASK_UPDATED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(TASK_ID), eq(REQUEST_ID), eq("Task updated"),
                    metadataCaptor.capture());

            Map<String, Object> metadata = metadataCaptor.getValue();
            assertThat(metadata).containsEntry("titleChanged", true);
            assertThat(metadata).containsEntry("descriptionChanged", true);
            assertThat(metadata).containsEntry("priorityChanged", true);
            assertThat(metadata).containsEntry("dueDateChanged", true);
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

    private Task createTask() {
        return Task.create(TASK_ID, WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                "Original Title", "Original Description", TaskPriority.MEDIUM,
                LocalDate.of(2026, 7, 15));
    }
}
