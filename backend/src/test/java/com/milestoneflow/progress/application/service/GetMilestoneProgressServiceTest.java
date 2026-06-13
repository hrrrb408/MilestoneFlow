package com.milestoneflow.progress.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.TaskCountProjection;
import com.milestoneflow.progress.application.result.MilestoneProgressResult;
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
 * Unit tests for {@link GetMilestoneProgressService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetMilestoneProgressService")
class GetMilestoneProgressServiceTest {

    @Mock private ProgressQueryRepository progressQueryRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;

    private GetMilestoneProgressService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MILESTONE_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new GetMilestoneProgressService(
                progressQueryRepository, projectRepository,
                milestoneRepository, workspaceAccessChecker);
    }

    @Nested
    @DisplayName("getMilestoneProgress")
    class GetMilestoneProgress {

        @Test
        @DisplayName("should return milestone progress with correct task counts")
        void shouldReturnMilestoneProgress() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(progressQueryRepository.countTasksByMilestone(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(new TaskCountProjection(5, 2));

            MilestoneProgressResult result = service.getMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, USER_ID, REQUEST_ID);

            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.projectId()).isEqualTo(PROJECT_ID);
            assertThat(result.milestoneId()).isEqualTo(MILESTONE_ID);
            assertThat(result.milestoneTitle()).isEqualTo("Test Milestone");
            assertThat(result.milestoneStatus()).isEqualTo("OPEN");
            assertThat(result.totalTasks()).isEqualTo(5);
            assertThat(result.completedTasks()).isEqualTo(2);
            assertThat(result.openTasks()).isEqualTo(3);
            assertThat(result.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
        }

        @Test
        @DisplayName("should return 0.00% when milestone has no tasks")
        void shouldReturnZeroWhenNoTasks() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(Optional.of(createMilestone()));
            when(progressQueryRepository.countTasksByMilestone(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID))
                    .thenReturn(new TaskCountProjection(0, 0));

            MilestoneProgressResult result = service.getMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, USER_ID, REQUEST_ID);

            assertThat(result.totalTasks()).isEqualTo(0);
            assertThat(result.completedTasks()).isEqualTo(0);
            assertThat(result.openTasks()).isEqualTo(0);
            assertThat(result.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }

        @Test
        @DisplayName("should reject when not active member")
        void shouldRejectWhenNotActiveMember() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.getMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }

        @Test
        @DisplayName("should reject when project not found")
        void shouldRejectWhenProjectNotFound() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, USER_ID, REQUEST_ID))
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

            assertThatThrownBy(() -> service.getMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, MILESTONE_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(MilestoneNotFoundException.class);
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

    private Milestone createMilestone() {
        return Milestone.create(MILESTONE_ID, WORKSPACE_ID, PROJECT_ID,
                "Test Milestone", "description", LocalDate.of(2026, 7, 1));
    }
}
