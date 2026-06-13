package com.milestoneflow.progress.application.service;

import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.MilestoneCountProjection;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.TaskCountProjection;
import com.milestoneflow.progress.application.result.ProjectProgressResult;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetProjectProgressService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetProjectProgressService")
class GetProjectProgressServiceTest {

    @Mock private ProgressQueryRepository progressQueryRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;

    private GetProjectProgressService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new GetProjectProgressService(
                progressQueryRepository, projectRepository, workspaceAccessChecker);
    }

    @Nested
    @DisplayName("getProjectProgress")
    class GetProjectProgress {

        @Test
        @DisplayName("should return project progress with correct aggregation")
        void shouldReturnProjectProgress() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new TaskCountProjection(10, 4));
            when(progressQueryRepository.countMilestonesByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new MilestoneCountProjection(3, 1, 2));

            ProjectProgressResult result = service.getProjectProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.totalTasks()).isEqualTo(10);
            assertThat(result.completedTasks()).isEqualTo(4);
            assertThat(result.openTasks()).isEqualTo(6);
            assertThat(result.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
            assertThat(result.totalMilestones()).isEqualTo(3);
            assertThat(result.completedMilestones()).isEqualTo(1);
            assertThat(result.openMilestones()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return 0.00% when project has no tasks")
        void shouldReturnZeroWhenNoTasks() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new TaskCountProjection(0, 0));
            when(progressQueryRepository.countMilestonesByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new MilestoneCountProjection(2, 0, 2));

            ProjectProgressResult result = service.getProjectProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(result.totalTasks()).isEqualTo(0);
            assertThat(result.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }

        @Test
        @DisplayName("should return 100.00% when all tasks completed")
        void shouldReturn100WhenAllCompleted() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new TaskCountProjection(5, 5));
            when(progressQueryRepository.countMilestonesByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new MilestoneCountProjection(1, 1, 0));

            ProjectProgressResult result = service.getProjectProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(result.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
            assertThat(result.openTasks()).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject when not active member")
        void shouldRejectWhenNotActiveMember() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.getProjectProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }

        @Test
        @DisplayName("should reject when project not found")
        void shouldRejectWhenProjectNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProjectProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectNotFoundException.class);
        }

        @Test
        @DisplayName("should calculate precise rate for 2/3 tasks")
        void shouldCalculatePreciseRate() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new TaskCountProjection(3, 2));
            when(progressQueryRepository.countMilestonesByProject(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(new MilestoneCountProjection(1, 0, 1));

            ProjectProgressResult result = service.getProjectProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(result.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(66.67));
            assertThat(result.openTasks()).isEqualTo(1);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private WorkspaceMembership createMembership() {
        return WorkspaceMembership.createOwner(
                UUID.randomUUID(), WORKSPACE_ID, USER_ID, Instant.now());
    }

    private Project createProject() {
        return Project.create(PROJECT_ID, WORKSPACE_ID, "Test Project", "description",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
    }
}
