package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetTaskService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetTaskService")
class GetTaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;

    private GetTaskService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new GetTaskService(taskRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker);
    }

    @Nested
    @DisplayName("getTask")
    class GetTask {

        @Test
        @DisplayName("should get task successfully")
        void shouldGetTaskSuccessfully() {
            Task task = createTask();
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.of(task));

            TaskResult result = service.getTask(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    USER_ID, REQUEST_ID);

            assertThat(result.taskId()).isEqualTo(TASK_ID);
            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.milestoneId()).isEqualTo(MILESTONE_ID);
            assertThat(result.title()).isEqualTo("Test Task");
            assertThat(result.description()).isEqualTo("description");
            assertThat(result.status()).isEqualTo("OPEN");
            assertThat(result.priority()).isEqualTo("MEDIUM");
            assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        }

        @Test
        @DisplayName("should throw TaskNotFoundException when task not found")
        void shouldThrowTaskNotFoundExceptionWhenTaskNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTask(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    USER_ID, REQUEST_ID))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ProjectNotFoundException when project not found")
        void shouldThrowProjectNotFoundExceptionWhenProjectNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTask(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("should throw MilestoneNotFoundException when milestone not found")
        void shouldThrowMilestoneNotFoundExceptionWhenMilestoneNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTask(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneNotFoundException.class);
        }

        @Test
        @DisplayName("should reject when not active member")
        void shouldRejectWhenNotActiveMember() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.getTask(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, TASK_ID,
                    USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
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
                "Test Task", "description", TaskPriority.MEDIUM,
                LocalDate.of(2026, 7, 15));
    }
}
