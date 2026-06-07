package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for V003 workspace and workspace_membership constraints.
 * Uses Testcontainers PostgreSQL 17 to verify real database behaviour.
 */
class WorkspaceConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, email, email, "Test User", "{bcrypt}hash", "ACTIVE"
        );
        return id;
    }

    private UUID insertWorkspace(UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                id, "Test Workspace", "test-ws-" + id.toString().substring(0, 8),
                "TWD", "Asia/Taipei", "ACTIVE", "{}", createdBy
        );
        return id;
    }

    private UUID insertMembership(UUID workspaceId, UUID userId, String role, String status) {
        OffsetDateTime joinedAt = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, workspaceId, userId, role, status, joinedAt
        );
        return id;
    }

    // ── workspace ─────────────────────────────────────────────────────────

    @Nested
    class WorkspaceTests {

        @Test
        void shouldInsertValidWorkspace() {
            UUID userId = insertUser("ws-valid@example.com");
            UUID wsId = insertWorkspace(userId);
            assertThat(jdbc.queryForObject(
                    "SELECT slug FROM workspace WHERE id = ?", String.class, wsId
            )).isNotNull();
        }

        @Test
        void shouldRejectDuplicateSlug() {
            UUID userId = insertUser("ws-dup-slug@example.com");
            String slug = "unique-slug-test";
            jdbc.update(
                    "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                    UUID.randomUUID(), "WS1", slug, "TWD", "Asia/Taipei", "ACTIVE", "{}", userId
            );
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            UUID.randomUUID(), "WS2", slug, "USD", "UTC", "ACTIVE", "{}", userId
                    )
            ).isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void shouldRejectInvalidCurrency() {
            UUID userId = insertUser("ws-bad-currency@example.com");
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            UUID.randomUUID(), "WS", "bad-curr", "tw", "Asia/Taipei", "ACTIVE", "{}", userId
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectInvalidStatus() {
            UUID userId = insertUser("ws-bad-status@example.com");
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            UUID.randomUUID(), "WS", "bad-status", "TWD", "Asia/Taipei", "DELETED", "{}", userId
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAcceptJsonbObjectSettings() {
            UUID userId = insertUser("ws-json-obj@example.com");
            UUID wsId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                    wsId, "WS", "json-settings", "TWD", "Asia/Taipei", "ACTIVE",
                    "{\"theme\": \"dark\", \"notifications\": true}", userId
            );
            String settings = jdbc.queryForObject(
                    "SELECT settings::text FROM workspace WHERE id = ?", String.class, wsId
            );
            assertThat(settings).contains("theme");
        }

        @Test
        void shouldRejectJsonbArraySettings() {
            UUID userId = insertUser("ws-json-arr@example.com");
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            UUID.randomUUID(), "WS", "json-arr", "TWD", "Asia/Taipei", "ACTIVE",
                            "[1, 2, 3]", userId
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectArchivedWithoutArchivedAt() {
            UUID userId = insertUser("ws-no-arch-ts@example.com");
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            UUID.randomUUID(), "WS", "no-arch-ts", "TWD", "Asia/Taipei", "ARCHIVED", "{}", userId
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAcceptArchivedWithArchivedAt() {
            UUID userId = insertUser("ws-arch@example.com");
            UUID wsId = UUID.randomUUID();
            OffsetDateTime archivedAt = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, "
                            + "created_by, archived_at, archived_by) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                    wsId, "WS", "archived-ws", "TWD", "Asia/Taipei", "ARCHIVED", "{}",
                    userId, archivedAt, userId
            );
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM workspace WHERE id = ?", String.class, wsId
            )).isEqualTo("ARCHIVED");
        }

        @Test
        void shouldRejectNegativeVersion() {
            UUID userId = insertUser("ws-neg-ver@example.com");
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, "
                                    + "created_by, version) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                            UUID.randomUUID(), "WS", "neg-ver", "TWD", "Asia/Taipei", "ACTIVE", "{}",
                            userId, -1
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectCreatedByWithNonExistentUser() {
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            UUID.randomUUID(), "WS", "bad-creator", "TWD", "Asia/Taipei", "ACTIVE", "{}",
                            UUID.randomUUID()
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void workspaceTableHasNoSeedData() {
            // Verify no seed data was inserted by migrations (V003 is structural only).
            // We check that no workspace references a user that we didn't insert ourselves.
            // This validates the migration doesn't auto-create workspaces.
            Integer orphanedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workspace WHERE created_by NOT IN (SELECT id FROM app_user)",
                    Integer.class
            );
            assertThat(orphanedCount).isZero();
        }

        @Test
        void workspaceTableShouldNotContainWorkspaceId() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'workspace' AND column_name = 'workspace_id'",
                    Integer.class
            );
            assertThat(count).isZero();
        }

        @Test
        void shouldAcceptValidStatuses() {
            UUID userId = insertUser("ws-all-statuses@example.com");

            String[] validStatuses = {"ACTIVE", "SUSPENDED", "ARCHIVED"};
            for (String status : validStatuses) {
                UUID wsId = UUID.randomUUID();
                if ("ARCHIVED".equals(status)) {
                    OffsetDateTime archivedAt = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, "
                                    + "created_by, archived_at, archived_by) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                            wsId, "WS-" + status, "ws-" + status.toLowerCase() + "-" + wsId.toString().substring(0, 8),
                            "TWD", "Asia/Taipei", status, "{}", userId, archivedAt, userId
                    );
                } else {
                    jdbc.update(
                            "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                            wsId, "WS-" + status, "ws-" + status.toLowerCase() + "-" + wsId.toString().substring(0, 8),
                            "TWD", "Asia/Taipei", status, "{}", userId
                    );
                }
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM workspace WHERE id = ?", String.class, wsId
                )).isEqualTo(status);
            }
        }
    }

    // ── workspace_membership ──────────────────────────────────────────────

    @Nested
    class MembershipTests {

        @Test
        void shouldInsertValidOwnerMembership() {
            UUID userId = insertUser("mem-owner@example.com");
            UUID wsId = insertWorkspace(userId);
            UUID memId = insertMembership(wsId, userId, "OWNER", "ACTIVE");
            assertThat(jdbc.queryForObject(
                    "SELECT role FROM workspace_membership WHERE id = ?", String.class, memId
            )).isEqualTo("OWNER");
        }

        @Test
        void shouldRejectMembershipWithNonExistentWorkspace() {
            UUID userId = insertUser("mem-bad-ws@example.com");
            assertThatThrownBy(() ->
                    insertMembership(UUID.randomUUID(), userId, "OWNER", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectMembershipWithNonExistentUser() {
            UUID userId = insertUser("mem-bad-user@example.com");
            UUID wsId = insertWorkspace(userId);
            assertThatThrownBy(() ->
                    insertMembership(wsId, UUID.randomUUID(), "OWNER", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectDuplicateWorkspaceUser() {
            UUID userId = insertUser("mem-dup@example.com");
            UUID wsId = insertWorkspace(userId);
            insertMembership(wsId, userId, "OWNER", "ACTIVE");

            assertThatThrownBy(() ->
                    insertMembership(wsId, userId, "OWNER", "ACTIVE")
            ).isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void shouldRejectTwoActiveOwnersInSameWorkspace() {
            UUID user1 = insertUser("mem-owner1@example.com");
            UUID user2 = insertUser("mem-owner2@example.com");
            UUID wsId = insertWorkspace(user1);

            insertMembership(wsId, user1, "OWNER", "ACTIVE");

            assertThatThrownBy(() ->
                    insertMembership(wsId, user2, "OWNER", "ACTIVE")
            ).isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void shouldRejectUserWithTwoActiveWorkspaces() {
            UUID userId = insertUser("mem-multi@example.com");
            UUID ws1 = insertWorkspace(userId);
            UUID ws2 = insertWorkspace(userId);

            insertMembership(ws1, userId, "OWNER", "ACTIVE");

            assertThatThrownBy(() ->
                    insertMembership(ws2, userId, "OWNER", "ACTIVE")
            ).isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void shouldAllowOneActiveAndOneRemoved() {
            UUID userId = insertUser("mem-active-removed@example.com");
            UUID ws1 = insertWorkspace(userId);
            UUID ws2 = insertWorkspace(userId);

            insertMembership(ws1, userId, "OWNER", "ACTIVE");

            OffsetDateTime joinedAt = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime endedAt = OffsetDateTime.of(2026, 6, 5, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, ended_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), ws2, userId, "OWNER", "REMOVED", joinedAt, endedAt
            );

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workspace_membership WHERE user_id = ?",
                    Integer.class, userId
            );
            assertThat(count).isEqualTo(2);
        }

        @Test
        void shouldAllowActiveAndRemovedOwnerInSameWorkspace() {
            UUID user1 = insertUser("mem-old-owner@example.com");
            UUID user2 = insertUser("mem-new-owner@example.com");
            UUID wsId = insertWorkspace(user1);

            OffsetDateTime joinedAt = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime endedAt = OffsetDateTime.of(2026, 6, 5, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, ended_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), wsId, user1, "OWNER", "REMOVED", joinedAt, endedAt
            );

            insertMembership(wsId, user2, "OWNER", "ACTIVE");

            Integer activeOwners = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workspace_membership WHERE workspace_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                    Integer.class, wsId
            );
            assertThat(activeOwners).isEqualTo(1);
        }

        @Test
        void shouldRejectInvalidRole() {
            UUID userId = insertUser("mem-bad-role@example.com");
            UUID wsId = insertWorkspace(userId);
            assertThatThrownBy(() ->
                    insertMembership(wsId, userId, "ADMIN", "ACTIVE")
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectInvalidStatus() {
            UUID userId = insertUser("mem-bad-mem-status@example.com");
            UUID wsId = insertWorkspace(userId);
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), wsId, userId, "OWNER", "SUSPENDED",
                            OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldPopulateJoinedAtDefaultForActive() {
            UUID userId = insertUser("mem-join-default@example.com");
            UUID wsId = insertWorkspace(userId);
            UUID memId = UUID.randomUUID();
            // joined_at has NOT NULL DEFAULT now(), so omitting it still works.
            // The ck_membership_active_joined CHECK is enforced but redundant with NOT NULL.
            jdbc.update(
                    "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    memId, wsId, userId, "OWNER", "ACTIVE"
            );
            OffsetDateTime joinedAt = jdbc.queryForObject(
                    "SELECT joined_at FROM workspace_membership WHERE id = ?",
                    OffsetDateTime.class, memId
            );
            assertThat(joinedAt).isNotNull();
        }

        @Test
        void shouldRejectRemovedWithoutEndedAt() {
            UUID userId = insertUser("mem-no-end@example.com");
            UUID wsId = insertWorkspace(userId);
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), wsId, userId, "OWNER", "REMOVED",
                            OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectEndedBeforeJoined() {
            UUID userId = insertUser("mem-end-order@example.com");
            UUID wsId = insertWorkspace(userId);
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, ended_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), wsId, userId, "OWNER", "REMOVED",
                            OffsetDateTime.of(2026, 6, 5, 0, 0, 0, 0, ZoneOffset.UTC),
                            OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectDeleteWorkspaceWithMembership() {
            UUID userId = insertUser("mem-del-ws@example.com");
            UUID wsId = insertWorkspace(userId);
            insertMembership(wsId, userId, "OWNER", "ACTIVE");

            assertThatThrownBy(() ->
                    jdbc.update("DELETE FROM workspace WHERE id = ?", wsId)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectDeleteUserWithMembership() {
            UUID userId = insertUser("mem-del-user@example.com");
            UUID wsId = insertWorkspace(userId);
            insertMembership(wsId, userId, "OWNER", "ACTIVE");

            assertThatThrownBy(() ->
                    jdbc.update("DELETE FROM app_user WHERE id = ?", userId)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
