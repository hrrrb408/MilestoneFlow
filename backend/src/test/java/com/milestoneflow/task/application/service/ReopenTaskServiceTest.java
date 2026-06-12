package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskNotCompletedException;
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
 * Unit tests for ReopenTaskService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReopenTaskService")
class ReopenTaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock private TaskAuditWriter auditWriter;

    private ReopenTaskService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-reopen-123";

    @BeforeEach
    void setUp() {
        service = new ReopenTaskService(taskRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker, auditWriter);
    }

    @Nested
    @DisplayName("reopen")
    class Reopen {

        @Test
        @DisplayName("should reopen completed task successfully as owner")
        void shouldReopenCompletedTaskSuccessfully() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            Task completedTask = createCompletedTask();
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(completedTask));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            TaskResult result = service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID);

            assertThat(result.taskId()).isEqualTo(TASK_ID);
            assertThat(result.status()).isEqualTo("OPEN");
            assertThat(result.completedAt()).isNull();
        }

        @Test
        @DisplayName("should throw TaskNotFoundException when task not found")
        void shouldThrowTaskNotFoundException() {
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

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("should throw TaskNotCompletedException when task is OPEN")
        void shouldThrowTaskNotCompletedException() {
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

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskNotCompletedException.class);
        }

        @Test
        @DisplayName("should throw ProjectArchivedException when project is archived")
        void shouldThrowProjectArchivedException() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            Project archivedProject = createProject();
            archivedProject.archive(USER_ID, Instant.now());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(archivedProject));

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectArchivedException.class);
        }

        @Test
        @DisplayName("should throw MilestoneCompletedException when milestone is completed")
        void shouldThrowMilestoneCompletedException() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            Milestone completedMilestone = createMilestone();
            completedMilestone.complete(USER_ID, Instant.now());
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(completedMilestone));

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneCompletedException.class);
        }

        @Test
        @DisplayName("should throw WorkspaceOwnerRequiredException when not owner")
        void shouldThrowWorkspaceOwnerRequiredException() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceOwnerRequiredException());

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceOwnerRequiredException.class);
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when project not found")
        void shouldThrowProjectNotFoundException() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("should throw MilestoneNotFoundException when milestone not found")
        void shouldThrowMilestoneNotFoundException() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneNotFoundException.class);
        }

        @Test
        @DisplayName("should call audit writer with TASK_REOPENED")
        void shouldCallAuditWriter() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(createCompletedTask()));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            service.reopen(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID);

            ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(auditWriter).writeUserEvent(
                    eq("TASK_REOPENED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(TASK_ID), eq(REQUEST_ID), eq("Task reopened"),
                    metadataCaptor.capture());

            Map<String, Object> metadata = metadataCaptor.getValue();
            assertThat(metadata).containsEntry("previousStatus", "COMPLETED");
            assertThat(metadata).containsEntry("newStatus", "OPEN");
        }
    }

    // ── Helper methods ───────────────────────────────────────────────────

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
                "Test Task", "Description", TaskPriority.MEDIUM,
                LocalDate.of(2026, 7, 15));
    }

    private Task createCompletedTask() {
        Task task = createTask();
        task.complete(USER_ID, Instant.now());
        return task;
    }
}
