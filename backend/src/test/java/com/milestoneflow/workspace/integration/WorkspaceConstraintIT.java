package com.milestoneflow.workspace.integration;

import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests verifying database constraints on workspace tables.
 */
@DisplayName("Workspace Constraint IT")
class WorkspaceConstraintIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM workspace_membership");
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");
        jdbc.update("DELETE FROM workspace");
    }

    // ── workspace.slug unique constraint ─────────────────────────────────

    @Nested
    @DisplayName("workspace.slug unique")
    class SlugUnique {

        @Test
        @DisplayName("should reject duplicate slug")
        void shouldRejectDuplicateSlug() {
            jdbc.update("""
                INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at)
                VALUES (gen_random_uuid(), 'First', 'unique-slug-test', 'TWD', 'Asia/Taipei', 'ACTIVE', 0, now(), now())
                """);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), 'Second', 'unique-slug-test', 'TWD', 'Asia/Taipei', 'ACTIVE', 0, now(), now())
                        """)
            ).hasMessageContaining("uk_workspace_slug");
        }
    }

    // ── workspace.status CHECK constraint ────────────────────────────────

    @Nested
    @DisplayName("workspace.status CHECK")
    class StatusCheck {

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), 'Test', 'invalid-status', 'TWD', 'Asia/Taipei', 'INVALID', 0, now(), now())
                        """)
            ).hasMessageContaining("ck_workspace_status");
        }
    }

    // ── workspace.default_currency CHECK ─────────────────────────────────

    @Nested
    @DisplayName("workspace.default_currency CHECK")
    class CurrencyCheck {

        @Test
        @DisplayName("should reject lowercase currency")
        void shouldRejectLowercaseCurrency() {
            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), 'Test', 'lower-currency', 'twd', 'Asia/Taipei', 'ACTIVE', 0, now(), now())
                        """)
            ).hasMessageContaining("ck_workspace_currency");
        }

        @Test
        @DisplayName("should reject 4-letter currency")
        void shouldReject4LetterCurrency() {
            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), 'Test', 'long-currency', 'TWDD', 'Asia/Taipei', 'ACTIVE', 0, now(), now())
                        """)
            ).hasMessageMatching(".*(ck_workspace_currency|value too long).*");
        }
    }

    // ── workspace_membership role CHECK ──────────────────────────────────

    @Nested
    @DisplayName("workspace_membership.role CHECK")
    class RoleCheck {

        @Test
        @DisplayName("should only allow OWNER role in V0.1")
        void shouldOnlyAllowOwner() {
            var wsId = jdbc.queryForObject(
                    "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at) " +
                            "VALUES (gen_random_uuid(), 'Role Test', 'role-test', 'TWD', 'Asia/Taipei', 'ACTIVE', 0, now(), now()) RETURNING id::text",
                    String.class);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?::uuid, gen_random_uuid(), 'ADMIN', 'ACTIVE', now(), 0, now(), now())
                        """, wsId)
            ).hasMessageContaining("ck_membership_role");
        }
    }

    // ── workspace_membership active user unique index ────────────────────

    @Nested
    @DisplayName("workspace_membership active user unique")
    class ActiveUserUnique {

        @Test
        @DisplayName("should only allow one ACTIVE membership per user")
        void shouldAllowOnlyOneActive() {
            var wsId1 = jdbc.queryForObject(
                    "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at) " +
                            "VALUES (gen_random_uuid(), 'WS1', 'active-user-ws1', 'TWD', 'Asia/Taipei', 'ACTIVE', 0, now(), now()) RETURNING id::text",
                    String.class);
            var wsId2 = jdbc.queryForObject(
                    "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, version, created_at, updated_at) " +
                            "VALUES (gen_random_uuid(), 'WS2', 'active-user-ws2', 'TWD', 'Asia/Taipei', 'ACTIVE', 0, now(), now()) RETURNING id::text",
                    String.class);
            var userId = jdbc.queryForObject(
                    "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at, email_verified_at) " +
                            "VALUES (gen_random_uuid(), 'active-user@test.com', 'active-user@test.com', 'Test', 'hash', 'ACTIVE', 'en', 0, now(), now(), now()) RETURNING id::text",
                    String.class);

            jdbc.update("""
                INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, version, created_at, updated_at)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'OWNER', 'ACTIVE', now(), 0, now(), now())
                """, wsId1, userId);

            assertThatThrownBy(() ->
                    jdbc.update("""
                        INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, version, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'OWNER', 'ACTIVE', now(), 0, now(), now())
                        """, wsId2, userId)
            ).hasMessageContaining("uk_workspace_membership_active_user");
        }
    }
}
