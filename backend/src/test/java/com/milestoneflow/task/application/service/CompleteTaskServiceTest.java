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
import com.milestoneflow.task.domain.exception.TaskAlreadyCompletedException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * Unit tests for CompleteTaskService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompleteTaskService")
class CompleteTaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock private TaskAuditWriter auditWriter;

    private CompleteTaskService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-complete-123";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-13T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new CompleteTaskService(taskRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker, auditWriter, FIXED_CLOCK);
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("should complete task successfully as owner")
        void shouldCompleteTaskSuccessfully() {
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

            TaskResult result = service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID);

            assertThat(result.taskId()).isEqualTo(TASK_ID);
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.completedAt()).isNotNull();
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

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("should throw TaskAlreadyCompletedException when task already completed")
        void shouldThrowTaskAlreadyCompletedException() {
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

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskAlreadyCompletedException.class);
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

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
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

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneCompletedException.class);
        }

        @Test
        @DisplayName("should throw WorkspaceOwnerRequiredException when not owner")
        void shouldThrowWorkspaceOwnerRequiredException() {
            when(workspaceAccessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceOwnerRequiredException());

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
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

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
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

            assertThatThrownBy(() -> service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneNotFoundException.class);
        }

        @Test
        @DisplayName("should call audit writer with TASK_COMPLETED")
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
                    .thenReturn(Optional.of(createTask()));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            service.complete(WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    TASK_ID, USER_ID, REQUEST_ID);

            ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(auditWriter).writeUserEvent(
                    eq("TASK_COMPLETED"), eq(USER_ID), eq(WORKSPACE_ID),
                    eq(TASK_ID), eq(REQUEST_ID), eq("Task completed"),
                    metadataCaptor.capture());

            Map<String, Object> metadata = metadataCaptor.getValue();
            assertThat(metadata).containsEntry("previousStatus", "OPEN");
            assertThat(metadata).containsEntry("newStatus", "COMPLETED");
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
}
