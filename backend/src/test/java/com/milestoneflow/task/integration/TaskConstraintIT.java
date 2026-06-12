package com.milestoneflow.task.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constraint integration tests for task table.
 *
 * <p>Verifies database-level constraints (FK, CHECK, NOT NULL)
 * and Flyway migration integrity.
 */
@DisplayName("Task Constraint IT")
class TaskConstraintIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    // ── Helper methods ────────────────────────────────────────────────────

    private UUID insertWorkspace(String slug) {
        UUID id = UUID.randomUUID();
        UUID owner = insertUser("constraint-ws-owner-" + slug + "@example.com");
        jdbc.update("""
            INSERT INTO workspace (id, name, slug, settings, version, created_at, updated_at)
            VALUES (?, ?, ?, '{}', 0, now(), now())
            """, id, "Constraint WS " + slug, slug);
        jdbc.update("""
            INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, version, created_at, updated_at)
            VALUES (?, ?, ?, 'OWNER', 'ACTIVE', now(), 0, now(), now())
            """, UUID.randomUUID(), id, owner);
        return id;
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at)
            VALUES (?, ?, ?, 'Constraint User', '{bcrypt}hash', 'ACTIVE', 'en', 0, now(), now(), now())
            """, id, email, email.toLowerCase());
        return id;
    }

    private UUID insertProject(UUID workspaceId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO project (id, workspace_id, name, status, settings, version, created_at, updated_at)
            VALUES (?, ?, ?, 'ACTIVE', '{}', 0, now(), now())
            """, id, workspaceId, "Constraint Project");
        return id;
    }

    private UUID insertMilestone(UUID workspaceId, UUID projectId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO milestone (id, workspace_id, project_id, title, status, settings, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'OPEN', '{}', 0, now(), now())
            """, id, workspaceId, projectId, "Constraint Milestone");
        return id;
    }

    // ── Table existence ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Task table")
    class TaskTable {

        @Test
        @DisplayName("task table should exist")
        void taskTableShouldExist() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = 'task'",
                    Integer.class);
            assertThat(count).isGreaterThan(0);
        }
    }

    // ── Status CHECK constraint ───────────────────────────────────────────

    @Nested
    @DisplayName("Status CHECK")
    class StatusCheck {

        @Test
        @DisplayName("should accept OPEN status")
        void shouldAcceptOpen() {
            UUID wsId = insertWorkspace("status-open");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            UUID taskId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'OPEN', 'MEDIUM', '{}', 0, now(), now())
                """, taskId, wsId, projId, msId, "Open Task");

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM task WHERE id = ?", String.class, taskId))
                    .isEqualTo("OPEN");
        }

        @Test
        @DisplayName("should accept COMPLETED status")
        void shouldAcceptCompleted() {
            UUID wsId = insertWorkspace("status-completed");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            UUID taskId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'COMPLETED', 'MEDIUM', '{}', 0, now(), now())
                """, taskId, wsId, projId, msId, "Completed Task");

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM task WHERE id = ?", String.class, taskId))
                    .isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            UUID wsId = insertWorkspace("status-invalid");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'INVALID', 'MEDIUM', '{}', 0, now(), now())
                        """, UUID.randomUUID(), wsId, projId, msId, "Invalid Status Task")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── Priority CHECK constraint ─────────────────────────────────────────

    @Nested
    @DisplayName("Priority CHECK")
    class PriorityCheck {

        @Test
        @DisplayName("should accept LOW priority")
        void shouldAcceptLow() {
            UUID wsId = insertWorkspace("prio-low");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            UUID taskId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'OPEN', 'LOW', '{}', 0, now(), now())
                """, taskId, wsId, projId, msId, "Low Task");

            assertThat(jdbc.queryForObject(
                    "SELECT priority FROM task WHERE id = ?", String.class, taskId))
                    .isEqualTo("LOW");
        }

        @Test
        @DisplayName("should reject invalid priority")
        void shouldRejectInvalidPriority() {
            UUID wsId = insertWorkspace("prio-invalid");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'OPEN', 'URGENT', '{}', 0, now(), now())
                        """, UUID.randomUUID(), wsId, projId, msId, "Invalid Prio Task")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── Settings CHECK constraint ─────────────────────────────────────────

    @Nested
    @DisplayName("Settings CHECK")
    class SettingsCheck {

        @Test
        @DisplayName("should accept valid JSONB object")
        void shouldAcceptValidJsonb() {
            UUID wsId = insertWorkspace("settings-ok");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            jdbc.update("""
                INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'OPEN', 'MEDIUM', '{"key": "value"}', 0, now(), now())
                """, UUID.randomUUID(), wsId, projId, msId, "Settings Task");
        }

        @Test
        @DisplayName("should reject non-object settings")
        void shouldRejectNonObjectSettings() {
            UUID wsId = insertWorkspace("settings-invalid");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'OPEN', 'MEDIUM", '"array"', 0, now(), now())
                        """, UUID.randomUUID(), wsId, projId, msId, "Bad Settings Task")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── FK constraints ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Foreign key constraints")
    class ForeignKeyConstraints {

        @Test
        @DisplayName("should reject non-existent workspace_id")
        void shouldRejectNonExistentWorkspace() {
            UUID fakeWs = UUID.randomUUID();
            UUID wsId = insertWorkspace("fk-ws");
            UUID projId = insertProject(wsId);
            UUID msId = insertMilestone(wsId, projId);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'OPEN', 'MEDIUM', '{}', 0, now(), now())
                        """, UUID.randomUUID(), fakeWs, projId, msId, "FK Task")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should reject non-existent project_id")
        void shouldRejectNonExistentProject() {
            UUID wsId = insertWorkspace("fk-proj");
            UUID fakeProj = UUID.randomUUID();
            UUID msId = UUID.randomUUID();

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'OPEN', 'MEDIUM', '{}', 0, now(), now())
                        """, UUID.randomUUID(), wsId, fakeProj, msId, "FK Task")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should reject non-existent milestone_id")
        void shouldRejectNonExistentMilestone() {
            UUID wsId = insertWorkspace("fk-ms");
            UUID projId = insertProject(wsId);
            UUID fakeMs = UUID.randomUUID();

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO task (id, workspace_id, project_id, milestone_id, title, status, priority, settings, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'OPEN', 'MEDIUM', '{}', 0, now(), now())
                        """, UUID.randomUUID(), wsId, projId, fakeMs, "FK Task")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── Flyway migrations ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Flyway migrations")
    class FlywayMigrations {

        @Test
        @DisplayName("V010 task migration should be applied")
        void v010ShouldBeApplied() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10' AND success = true",
                    Integer.class);
            assertThat(count).isGreaterThan(0);
        }

        @Test
        @DisplayName("should have no failed migrations")
        void shouldHaveNoFailedMigrations() {
            Integer failed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                    Integer.class);
            assertThat(failed).isZero();
        }
    }
}
