package com.milestoneflow.task.domain;

import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Task domain model")
class TaskTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID WS_ID = UUID.randomUUID();
    private static final UUID PROJ_ID = UUID.randomUUID();
    private static final UUID MS_ID = UUID.randomUUID();

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create task with OPEN status by default")
        void shouldCreateWithOpenStatus() {
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Test Task",
                    "Description", TaskPriority.HIGH, LocalDate.of(2026, 7, 15));

            assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
        }

        @Test
        @DisplayName("should create task with default priority MEDIUM")
        void shouldCreateWithMediumPriority() {
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Test Task",
                    null, TaskPriority.MEDIUM, null);

            assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        }

        @Test
        @DisplayName("should set workspaceId, projectId, milestoneId")
        void shouldSetIds() {
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Test Task",
                    null, TaskPriority.LOW, null);

            assertThat(task.getId()).isEqualTo(ID);
            assertThat(task.getWorkspaceId()).isEqualTo(WS_ID);
            assertThat(task.getProjectId()).isEqualTo(PROJ_ID);
            assertThat(task.getMilestoneId()).isEqualTo(MS_ID);
        }

        @Test
        @DisplayName("should set title, description, priority, dueDate")
        void shouldSetFields() {
            LocalDate dueDate = LocalDate.of(2026, 8, 1);
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Title",
                    "Description", TaskPriority.HIGH, dueDate);

            assertThat(task.getTitle()).isEqualTo("Title");
            assertThat(task.getDescription()).isEqualTo("Description");
            assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(task.getDueDate()).isEqualTo(dueDate);
        }

        @Test
        @DisplayName("should have null completedAt and completedBy")
        void shouldHaveNullCompletionFields() {
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Test",
                    null, TaskPriority.MEDIUM, null);

            assertThat(task.getCompletedAt()).isNull();
            assertThat(task.getCompletedBy()).isNull();
        }

        @Test
        @DisplayName("should reject null title")
        void shouldRejectNullTitle() {
            assertThatThrownBy(() -> Task.create(ID, WS_ID, PROJ_ID, MS_ID,
                    null, null, TaskPriority.MEDIUM, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("title");
        }

        @Test
        @DisplayName("should reject blank title")
        void shouldRejectBlankTitle() {
            assertThatThrownBy(() -> Task.create(ID, WS_ID, PROJ_ID, MS_ID,
                    "   ", null, TaskPriority.MEDIUM, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("should reject title too long")
        void shouldRejectTitleTooLong() {
            assertThatThrownBy(() -> Task.create(ID, WS_ID, PROJ_ID, MS_ID,
                    "x".repeat(161), null, TaskPriority.MEDIUM, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("160");
        }
    }

    @Nested
    @DisplayName("updateBasicInfo()")
    class UpdateBasicInfo {

        private Task createDefault() {
            return Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Original Title",
                    "Original Desc", TaskPriority.MEDIUM,
                    LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("should update title")
        void shouldUpdateTitle() {
            Task task = createDefault();
            task.updateBasicInfo("New Title", null, null, null);
            assertThat(task.getTitle()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("should update description")
        void shouldUpdateDescription() {
            Task task = createDefault();
            task.updateBasicInfo(null, "New Desc", null, null);
            assertThat(task.getDescription()).isEqualTo("New Desc");
        }

        @Test
        @DisplayName("should update priority")
        void shouldUpdatePriority() {
            Task task = createDefault();
            task.updateBasicInfo(null, null, TaskPriority.HIGH, null);
            assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        }

        @Test
        @DisplayName("should update dueDate")
        void shouldUpdateDueDate() {
            Task task = createDefault();
            LocalDate newDate = LocalDate.of(2026, 12, 31);
            task.updateBasicInfo(null, null, null, newDate);
            assertThat(task.getDueDate()).isEqualTo(newDate);
        }

        @Test
        @DisplayName("should skip null parameters")
        void shouldSkipNullParameters() {
            Task task = createDefault();
            task.updateBasicInfo(null, null, null, null);
            assertThat(task.getTitle()).isEqualTo("Original Title");
            assertThat(task.getDescription()).isEqualTo("Original Desc");
            assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        }

        @Test
        @DisplayName("should reject blank title on update")
        void shouldRejectBlankTitle() {
            Task task = createDefault();
            assertThatThrownBy(() -> task.updateBasicInfo("  ", null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("should reject title too long on update")
        void shouldRejectTitleTooLong() {
            Task task = createDefault();
            assertThatThrownBy(() -> task.updateBasicInfo("x".repeat(161), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("160");
        }
    }

    @Nested
    @DisplayName("isCompleted()")
    class IsCompleted {

        @Test
        @DisplayName("should return false for OPEN task")
        void shouldReturnFalseForOpen() {
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Test",
                    null, TaskPriority.MEDIUM, null);
            assertThat(task.isCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("should not include settings")
        void shouldNotIncludeSettings() {
            Task task = Task.create(ID, WS_ID, PROJ_ID, MS_ID, "Test",
                    null, TaskPriority.MEDIUM, null);

            String str = task.toString();
            assertThat(str).doesNotContain("settings");
            assertThat(str).contains("Task{");
            assertThat(str).contains("title='Test'");
        }
    }
}
