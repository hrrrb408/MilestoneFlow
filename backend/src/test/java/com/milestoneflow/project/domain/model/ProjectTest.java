package com.milestoneflow.project.domain.model;

import com.milestoneflow.project.domain.type.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Project domain entity.
 */
@DisplayName("Project")
class ProjectTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create project with ACTIVE status")
        void shouldCreateWithActiveStatus() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test Project", "desc",
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));

            assertThat(project.getId()).isEqualTo(ID);
            assertThat(project.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(project.getName()).isEqualTo("Test Project");
            assertThat(project.getDescription()).isEqualTo("desc");
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(project.getTargetDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("should create project with null dates")
        void shouldCreateWithNullDates() {
            Project project = Project.create(ID, WORKSPACE_ID, "No Dates", null, null, null);

            assertThat(project.getStartDate()).isNull();
            assertThat(project.getTargetDate()).isNull();
            assertThat(project.getDescription()).isNull();
        }

        @Test
        @DisplayName("should reject null workspaceId")
        void shouldRejectNullWorkspaceId() {
            assertThatThrownBy(() -> Project.create(ID, null, "Name", null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId");
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> Project.create(ID, WORKSPACE_ID, null, null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name");
        }
    }

    @Nested
    @DisplayName("updateBasicInfo")
    class UpdateBasicInfo {

        @Test
        @DisplayName("should update all fields")
        void shouldUpdateAllFields() {
            Project project = Project.create(ID, WORKSPACE_ID, "Original", "desc",
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));

            project.updateBasicInfo("Updated", "new desc",
                    LocalDate.of(2026, 6, 15), LocalDate.of(2026, 8, 1));

            assertThat(project.getName()).isEqualTo("Updated");
            assertThat(project.getDescription()).isEqualTo("new desc");
            assertThat(project.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 15));
            assertThat(project.getTargetDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("should skip null fields")
        void shouldSkipNullFields() {
            Project project = Project.create(ID, WORKSPACE_ID, "Original", "desc",
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));

            project.updateBasicInfo(null, null, null, null);

            assertThat(project.getName()).isEqualTo("Original");
            assertThat(project.getDescription()).isEqualTo("desc");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should not leak settings")
        void shouldNotLeakSettings() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);

            String str = project.toString();
            assertThat(str).doesNotContain("settings");
            assertThat(str).contains("Test");
        }
    }
}
