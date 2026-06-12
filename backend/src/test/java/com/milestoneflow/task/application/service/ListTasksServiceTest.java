package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.exception.TaskInvalidStatusException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ListTasksService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListTasksService")
class ListTasksServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;

    private ListTasksService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new ListTasksService(taskRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker);
    }

    @Nested
    @DisplayName("listTasks")
    class ListTasks {

        @Test
        @DisplayName("should list tasks successfully")
        void shouldListTasksSuccessfully() {
            Task task = createTask();
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(List.of(task));

            List<TaskResult> results = service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, null);

            assertThat(results).hasSize(1);
            TaskResult result = results.get(0);
            assertThat(result.taskId()).isEqualTo(task.getId());
            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.milestoneId()).isEqualTo(MILESTONE_ID);
            assertThat(result.title()).isEqualTo("Test Task");
            assertThat(result.status()).isEqualTo("OPEN");
            assertThat(result.priority()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("should filter by status")
        void shouldFilterByStatus() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatus(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TaskStatus.OPEN))
                    .thenReturn(List.of(createTask()));

            List<TaskResult> results = service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, "OPEN", null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("should filter by priority")
        void shouldFilterByPriority() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndPriority(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TaskPriority.HIGH))
                    .thenReturn(List.of(createTask(TaskPriority.HIGH)));

            List<TaskResult> results = service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, "HIGH");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).priority()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));

            assertThatThrownBy(() -> service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, "INVALID_STATUS", null))
                    .isInstanceOf(TaskInvalidStatusException.class);
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

            assertThatThrownBy(() -> service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, "INVALID_PRIORITY"))
                    .isInstanceOf(TaskInvalidPriorityException.class);
        }

        @Test
        @DisplayName("should return empty list when no tasks")
        void shouldReturnEmptyListWhenNoTasks() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Collections.emptyList());

            List<TaskResult> results = service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, null);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should reject when not active member")
        void shouldRejectWhenNotActiveMember() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, null))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }

        @Test
        @DisplayName("should reject when project not found")
        void shouldRejectWhenProjectNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, null))
                    .isInstanceOf(ProjectNotFoundException.class);
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

            assertThatThrownBy(() -> service.listTasks(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                    USER_ID, REQUEST_ID, null, null))
                    .isInstanceOf(MilestoneNotFoundException.class);
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
        return createTask(TaskPriority.MEDIUM);
    }

    private Task createTask(TaskPriority priority) {
        return Task.create(UUID.randomUUID(), WORKSPACE_ID, PROJECT_ID, MILESTONE_ID,
                "Test Task", "description", priority, LocalDate.of(2026, 7, 15));
    }
}
