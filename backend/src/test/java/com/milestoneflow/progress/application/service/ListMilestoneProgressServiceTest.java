package com.milestoneflow.progress.application.service;

import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.MilestoneTaskCountProjection;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListMilestoneProgressService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListMilestoneProgressService")
class ListMilestoneProgressServiceTest {

    @Mock private ProgressQueryRepository progressQueryRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WorkspaceAccessChecker workspaceAccessChecker;

    private ListMilestoneProgressService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new ListMilestoneProgressService(
                progressQueryRepository, projectRepository, workspaceAccessChecker);
    }

    @Nested
    @DisplayName("listMilestoneProgress")
    class ListMilestoneProgress {

        @Test
        @DisplayName("should return progress for each milestone")
        void shouldReturnProgressForMilestones() {
            UUID ms1 = UUID.randomUUID();
            UUID ms2 = UUID.randomUUID();

            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksPerMilestone(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(List.of(
                            new MilestoneTaskCountProjection(ms1, "Milestone A", 3, 2, "OPEN"),
                            new MilestoneTaskCountProjection(ms2, "Milestone B", 2, 0, "OPEN")
                    ));

            List<MilestoneProgressResult> results = service.listMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(results).hasSize(2);

            MilestoneProgressResult r1 = results.get(0);
            assertThat(r1.milestoneId()).isEqualTo(ms1);
            assertThat(r1.milestoneTitle()).isEqualTo("Milestone A");
            assertThat(r1.totalTasks()).isEqualTo(3);
            assertThat(r1.completedTasks()).isEqualTo(2);
            assertThat(r1.openTasks()).isEqualTo(1);
            assertThat(r1.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(66.67));

            MilestoneProgressResult r2 = results.get(1);
            assertThat(r2.milestoneId()).isEqualTo(ms2);
            assertThat(r2.milestoneTitle()).isEqualTo("Milestone B");
            assertThat(r2.totalTasks()).isEqualTo(2);
            assertThat(r2.completedTasks()).isEqualTo(0);
            assertThat(r2.openTasks()).isEqualTo(2);
            assertThat(r2.completionRate()).isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }

        @Test
        @DisplayName("should return empty list when no milestones")
        void shouldReturnEmptyList() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksPerMilestone(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Collections.emptyList());

            List<MilestoneProgressResult> results = service.listMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("milestone with zero tasks should show 0.00%")
        void milestoneWithZeroTasksShowsZero() {
            UUID ms = UUID.randomUUID();

            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(createMembership());
            when(projectRepository.findByWorkspaceIdAndId(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(Optional.of(createProject()));
            when(progressQueryRepository.countTasksPerMilestone(WORKSPACE_ID, PROJECT_ID))
                    .thenReturn(List.of(
                            new MilestoneTaskCountProjection(ms, "Empty Milestone", 0, 0, "OPEN")
                    ));

            List<MilestoneProgressResult> results = service.listMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).completionRate())
                    .isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }

        @Test
        @DisplayName("should reject when not active member")
        void shouldRejectWhenNotActiveMember() {
            when(workspaceAccessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.listMilestoneProgress(
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

            assertThatThrownBy(() -> service.listMilestoneProgress(
                    WORKSPACE_ID, PROJECT_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(ProjectNotFoundException.class);
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
