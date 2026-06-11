package com.milestoneflow.workspace.domain.model;

import com.milestoneflow.workspace.domain.type.WorkspaceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Workspace} domain model.
 */
class WorkspaceTest {

    private static final UUID FIXED_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");

    // ── Creation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create workspace with valid parameters")
        void shouldCreateValidWorkspace() {
            Workspace ws = Workspace.create(FIXED_ID, "My Workspace", "my-workspace", "TWD", "Asia/Taipei");

            assertThat(ws.getId()).isEqualTo(FIXED_ID);
            assertThat(ws.getName()).isEqualTo("My Workspace");
            assertThat(ws.getSlug()).isEqualTo("my-workspace");
            assertThat(ws.getDefaultCurrency()).isEqualTo("TWD");
            assertThat(ws.getTimezone()).isEqualTo("Asia/Taipei");
        }

        @Test
        @DisplayName("should start in ACTIVE status")
        void shouldStartActive() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");

            assertThat(ws.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
        }

        @Test
        @DisplayName("should have null archivedAt initially")
        void shouldHaveNullArchivedAt() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");

            assertThat(ws.getArchivedAt()).isNull();
            assertThat(ws.getArchivedBy()).isNull();
        }

        @Test
        @DisplayName("should reject null ID")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> Workspace.create(null, "Test", "test", "TWD", "Asia/Taipei"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id must not be null");
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> Workspace.create(FIXED_ID, null, "test", "TWD", "Asia/Taipei"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null slug")
        void shouldRejectNullSlug() {
            assertThatThrownBy(() -> Workspace.create(FIXED_ID, "Test", null, "TWD", "Asia/Taipei"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── updateBasicInfo ──────────────────────────────────────────────────

    @Nested
    @DisplayName("updateBasicInfo")
    class UpdateBasicInfo {

        @Test
        @DisplayName("should update name")
        void shouldUpdateName() {
            Workspace ws = Workspace.create(FIXED_ID, "Old", "old", "TWD", "Asia/Taipei");
            ws.updateBasicInfo("New", null, null);

            assertThat(ws.getName()).isEqualTo("New");
            assertThat(ws.getTimezone()).isEqualTo("Asia/Taipei"); // unchanged
        }

        @Test
        @DisplayName("should update timezone")
        void shouldUpdateTimezone() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");
            ws.updateBasicInfo(null, "UTC", null);

            assertThat(ws.getTimezone()).isEqualTo("UTC");
            assertThat(ws.getName()).isEqualTo("Test"); // unchanged
        }

        @Test
        @DisplayName("should update defaultCurrency")
        void shouldUpdateCurrency() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");
            ws.updateBasicInfo(null, null, "USD");

            assertThat(ws.getDefaultCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("should update all fields at once")
        void shouldUpdateAllFields() {
            Workspace ws = Workspace.create(FIXED_ID, "Old", "old", "TWD", "Asia/Taipei");
            ws.updateBasicInfo("New", "UTC", "USD");

            assertThat(ws.getName()).isEqualTo("New");
            assertThat(ws.getTimezone()).isEqualTo("UTC");
            assertThat(ws.getDefaultCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("should be no-op when all params are null")
        void shouldBeNoOpWhenAllNull() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");
            ws.updateBasicInfo(null, null, null);

            assertThat(ws.getName()).isEqualTo("Test");
            assertThat(ws.getTimezone()).isEqualTo("Asia/Taipei");
            assertThat(ws.getDefaultCurrency()).isEqualTo("TWD");
        }
    }

    // ── toString security ────────────────────────────────────────────────

    @Nested
    @DisplayName("toString security")
    class ToStringSecurity {

        @Test
        @DisplayName("should not include settings in toString")
        void shouldNotIncludeSettings() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");

            String result = ws.toString();

            assertThat(result).doesNotContain("settings");
        }
    }

    // ── Equality ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWithSameId() {
            Workspace ws1 = Workspace.create(FIXED_ID, "A", "a", "TWD", "Asia/Taipei");
            Workspace ws2 = Workspace.create(FIXED_ID, "B", "b", "USD", "UTC");

            assertThat(ws1).isEqualTo(ws2);
            assertThat(ws1.hashCode()).isEqualTo(ws2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different IDs")
        void shouldNotBeEqualWithDifferentId() {
            Workspace ws1 = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");
            Workspace ws2 = Workspace.create(UUID.fromString("0191f5a0-5678-7abc-8def-0123456789ab"),
                    "Test", "test", "TWD", "Asia/Taipei");

            assertThat(ws1).isNotEqualTo(ws2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            Workspace ws = Workspace.create(FIXED_ID, "Test", "test", "TWD", "Asia/Taipei");
            assertThat(ws).isNotEqualTo(null);
        }
    }
}
