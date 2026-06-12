package com.milestoneflow.project.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests verifying database constraints on project table.
 */
@DisplayName("Project Constraint IT")
class ProjectConstraintIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private String workspaceId;

    @AfterEach
    void tearDown() {
        if (workspaceId != null) {
            jdbc.update("DELETE FROM milestone WHERE workspace_id = ?::uuid", workspaceId);
            jdbc.update("DELETE FROM project WHERE workspace_id = ?::uuid", workspaceId);
        }
        jdbc.update("DELETE FROM milestone");
        jdbc.update("DELETE FROM project");
        jdbc.update("DELETE FROM workspace_membership");
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace");
    }

    private String createWorkspace() {
        return jdbc.queryForObject(
                "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at) " +
                        "VALUES (gen_random_uuid(), 'Constraint Test', 'constraint-test-ws', 'TWD', 'Asia/Taipei', 'ACTIVE', 0, now(), now()) RETURNING id::text",
                String.class);
    }

    // ── project.status CHECK constraint ───────────────────────────────────

    @Nested
    @DisplayName("project.status CHECK")
    class StatusCheck {

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            workspaceId = createWorkspace();

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO project (id, workspace_id, name, status, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?::uuid, 'Test', 'INVALID', 0, now(), now())
                        """, workspaceId)
            ).hasMessageContaining("ck_project_status");
        }
    }

    // ── project workspace FK ──────────────────────────────────────────────

    @Nested
    @DisplayName("project.workspace_id FK")
    class WorkspaceFk {

        @Test
        @DisplayName("should reject non-existent workspace")
        void shouldRejectNonExistentWorkspace() {
            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO project (id, workspace_id, name, status, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'Orphan', 'ACTIVE', 0, now(), now())
                        """)
            ).hasMessageContaining("fk_project_workspace");
        }
    }

    // ── project date range CHECK ──────────────────────────────────────────

    @Nested
    @DisplayName("project date range CHECK")
    class DateRangeCheck {

        @Test
        @DisplayName("should reject start_date after target_date")
        void shouldRejectStartAfterTarget() {
            workspaceId = createWorkspace();

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO project (id, workspace_id, name, status, start_date, target_date, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?::uuid, 'Bad Dates', 'ACTIVE', '2026-07-01', '2026-06-01', 0, now(), now())
                        """, workspaceId)
            ).hasMessageContaining("ck_project_date_range");
        }

        @Test
        @DisplayName("should allow valid date range")
        void shouldAllowValidDateRange() {
            workspaceId = createWorkspace();

            jdbc.update("""
                INSERT INTO project (id, workspace_id, name, status, start_date, target_date, version, created_at, updated_at)
                VALUES (gen_random_uuid(), ?::uuid, 'Good Dates', 'ACTIVE', '2026-06-01', '2026-07-01', 0, now(), now())
                """, workspaceId);

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project WHERE workspace_id = ?::uuid AND name = 'Good Dates'",
                    Integer.class, workspaceId);
            assertThat(count).isEqualTo(1);
        }
    }

    // ── project settings JSONB CHECK ──────────────────────────────────────

    @Nested
    @DisplayName("project.settings CHECK")
    class SettingsCheck {

        @Test
        @DisplayName("should reject non-object settings")
        void shouldRejectNonObjectSettings() {
            workspaceId = createWorkspace();

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO project (id, workspace_id, name, status, settings, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?::uuid, 'Bad Settings', 'ACTIVE', '"not-an-object"', 0, now(), now())
                        """, workspaceId)
            ).hasMessageContaining("ck_project_settings");
        }
    }

}
