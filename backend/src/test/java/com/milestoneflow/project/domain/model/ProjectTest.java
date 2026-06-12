package com.milestoneflow.project.domain.model;

import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotArchivedException;
import com.milestoneflow.project.domain.type.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
            assertThat(project.getArchivedAt()).isNull();
            assertThat(project.getArchivedBy()).isNull();
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
    @DisplayName("archive")
    class Archive {

        @Test
        @DisplayName("should archive ACTIVE project")
        void shouldArchiveActiveProject() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);
            UUID actorId = UUID.randomUUID();
            Instant archivedAt = Instant.now();

            project.archive(actorId, archivedAt);

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
            assertThat(project.getArchivedAt()).isEqualTo(archivedAt);
            assertThat(project.getArchivedBy()).isEqualTo(actorId);
            assertThat(project.isArchived()).isTrue();
        }

        @Test
        @DisplayName("should reject archiving already ARCHIVED project")
        void shouldRejectDoubleArchive() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);
            project.archive(UUID.randomUUID(), Instant.now());

            assertThatThrownBy(() -> project.archive(UUID.randomUUID(), Instant.now()))
                    .isInstanceOf(ProjectArchivedException.class);
        }

        @Test
        @DisplayName("should reject null actorId")
        void shouldRejectNullActorId() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);

            assertThatThrownBy(() -> project.archive(null, Instant.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("actorId");
        }

        @Test
        @DisplayName("should reject null archivedAt")
        void shouldRejectNullArchivedAt() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);

            assertThatThrownBy(() -> project.archive(UUID.randomUUID(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("archivedAt");
        }
    }

    @Nested
    @DisplayName("restore")
    class Restore {

        @Test
        @DisplayName("should restore ARCHIVED project")
        void shouldRestoreArchivedProject() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);
            project.archive(UUID.randomUUID(), Instant.now());

            project.restore();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.getArchivedAt()).isNull();
            assertThat(project.getArchivedBy()).isNull();
            assertThat(project.isArchived()).isFalse();
        }

        @Test
        @DisplayName("should reject restoring ACTIVE project")
        void shouldRejectRestoreActive() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);

            assertThatThrownBy(project::restore)
                    .isInstanceOf(ProjectNotArchivedException.class);
        }

        @Test
        @DisplayName("should allow archive after restore")
        void shouldAllowArchiveAfterRestore() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);
            UUID actor = UUID.randomUUID();
            project.archive(actor, Instant.now());
            project.restore();

            // Archive again should work
            project.archive(actor, Instant.now());
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
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

        @Test
        @DisplayName("should reject update on ARCHIVED project")
        void shouldRejectUpdateOnArchived() {
            Project project = Project.create(ID, WORKSPACE_ID, "Original", "desc", null, null);
            project.archive(UUID.randomUUID(), Instant.now());

            assertThatThrownBy(() -> project.updateBasicInfo("New", null, null, null))
                    .isInstanceOf(ProjectArchivedException.class);
        }
    }

    @Nested
    @DisplayName("isArchived")
    class IsArchived {

        @Test
        @DisplayName("should return false for ACTIVE project")
        void shouldReturnFalseForActive() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);
            assertThat(project.isArchived()).isFalse();
        }

        @Test
        @DisplayName("should return true for ARCHIVED project")
        void shouldReturnTrueForArchived() {
            Project project = Project.create(ID, WORKSPACE_ID, "Test", null, null, null);
            project.archive(UUID.randomUUID(), Instant.now());
            assertThat(project.isArchived()).isTrue();
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
