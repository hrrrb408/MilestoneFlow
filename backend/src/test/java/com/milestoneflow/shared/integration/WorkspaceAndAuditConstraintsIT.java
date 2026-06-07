package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for V003 workspace and V004 audit schema constraints.
 */
class WorkspaceAndAuditConstraintsIT extends AbstractIntegrationTest {

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

    private UUID insertWorkspace(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                        + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ACTIVE', '{}')",
                id, "Test Workspace " + slug, slug
        );
        return id;
    }

    private void insertOwnerMembership(UUID workspaceId, UUID userId) {
        jdbc.update(
                "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                        + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE')",
                UUID.randomUUID(), workspaceId, userId
        );
    }

    // ── workspace ─────────────────────────────────────────────────────────

    @Test
    void shouldInsertValidWorkspace() {
        UUID id = insertWorkspace("valid-ws");
        assertThat(jdbc.queryForObject(
                "SELECT slug FROM workspace WHERE id = ?", String.class, id
        )).isEqualTo("valid-ws");
    }

    @Test
    void shouldRejectDuplicateSlug() {
        insertWorkspace("dup-slug");
        assertThatThrownBy(() -> insertWorkspace("dup-slug"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                                + "VALUES (?, ?, ?, ?, 'Asia/Taipei', 'ACTIVE', '{}')",
                        UUID.randomUUID(), "Bad Currency", "bad-curr", "123"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectLowercaseCurrency() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                                + "VALUES (?, ?, ?, ?, 'Asia/Taipei', 'ACTIVE', '{}')",
                        UUID.randomUUID(), "Lower Currency", "lower-curr", "twd"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidStatus() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                                + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', ?, '{}')",
                        UUID.randomUUID(), "Bad Status", "bad-status", "DELETED"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptJsonObjectSettings() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                        + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ACTIVE', ?)",
                id, "JSON WS", "json-ws", "{\"key\":\"value\"}"
        );
        assertThat(jdbc.queryForObject(
                "SELECT settings FROM workspace WHERE id = ?", String.class, id
        )).contains("\"key\"");
    }

    @Test
    void shouldRejectJsonArraySettings() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                                + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ACTIVE', ?)",
                        UUID.randomUUID(), "Array WS", "arr-ws", "[1,2,3]"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectArchivedWithoutArchivedAt() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                                + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', ?, '{}')",
                        UUID.randomUUID(), "No Archive", "no-archive", "ARCHIVED"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptArchivedWithArchivedAt() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, archived_at) "
                        + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ARCHIVED', '{}', ?)",
                id, "Archived WS", "archived-ws",
                OffsetDateTime.of(2026, 6, 7, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workspace WHERE id = ?", String.class, id
        )).isEqualTo("ARCHIVED");
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, version) "
                                + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ACTIVE', '{}', ?)",
                        UUID.randomUUID(), "Neg Ver", "neg-ver", -1
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectCreatedByWithNonExistentUser() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings, created_by) "
                                + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ACTIVE', '{}', ?)",
                        UUID.randomUUID(), "Bad Creator", "bad-creator", UUID.randomUUID()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── workspace_membership ───────────────────────────────────────────────

    @Test
    void shouldInsertValidOwnerMembership() {
        UUID userId = insertUser("owner@example.com");
        UUID wsId = insertWorkspace("owner-ws");
        insertOwnerMembership(wsId, userId);
        assertThat(jdbc.queryForObject(
                "SELECT role FROM workspace_membership WHERE workspace_id = ? AND user_id = ?",
                String.class, wsId, userId
        )).isEqualTo("OWNER");
    }

    @Test
    void shouldRejectMembershipWithNonExistentWorkspace() {
        UUID userId = insertUser("no-ws@example.com");
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE')",
                        UUID.randomUUID(), UUID.randomUUID(), userId
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectMembershipWithNonExistentUser() {
        UUID wsId = insertWorkspace("no-user-ws");
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE')",
                        UUID.randomUUID(), wsId, UUID.randomUUID()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateWorkspaceUser() {
        UUID userId = insertUser("dup-member@example.com");
        UUID wsId = insertWorkspace("dup-member-ws");
        insertOwnerMembership(wsId, userId);

        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', 'REMOVED')",
                        UUID.randomUUID(), wsId, userId
                )
        ).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectTwoActiveOwnersInSameWorkspace() {
        UUID user1 = insertUser("owner1@example.com");
        UUID user2 = insertUser("owner2@example.com");
        UUID wsId = insertWorkspace("two-owner-ws");
        insertOwnerMembership(wsId, user1);

        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE')",
                        UUID.randomUUID(), wsId, user2
                )
        ).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectUserWithTwoActiveMemberships() {
        UUID userId = insertUser("two-ws@example.com");
        UUID ws1 = insertWorkspace("ws1");
        UUID ws2 = insertWorkspace("ws2");
        insertOwnerMembership(ws1, userId);

        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE')",
                        UUID.randomUUID(), ws2, userId
                )
        ).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldAllowActiveAndRemovedForSameUser() {
        UUID userId = insertUser("active-removed@example.com");
        UUID ws1 = insertWorkspace("active-rem-ws1");
        UUID ws2 = insertWorkspace("active-rem-ws2");
        insertOwnerMembership(ws1, userId);

        // Insert as REMOVED in another workspace — should succeed
        jdbc.update(
                "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, ended_at) "
                        + "VALUES (?, ?, ?, 'OWNER', 'REMOVED', now(), now())",
                UUID.randomUUID(), ws2, userId
        );

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workspace_membership WHERE user_id = ?",
                Integer.class, userId
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldAllowActiveAndRemovedOwnerInSameWorkspace() {
        UUID user1 = insertUser("replace-owner1@example.com");
        UUID user2 = insertUser("replace-owner2@example.com");
        UUID wsId = insertWorkspace("replace-owner-ws");
        insertOwnerMembership(wsId, user1);

        // user2 as REMOVED owner — should succeed (partial index only covers ACTIVE)
        jdbc.update(
                "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status, joined_at, ended_at) "
                        + "VALUES (?, ?, ?, 'OWNER', 'REMOVED', now(), now())",
                UUID.randomUUID(), wsId, user2
        );
    }

    @Test
    void shouldRejectInvalidRole() {
        UUID userId = insertUser("bad-role@example.com");
        UUID wsId = insertWorkspace("bad-role-ws");
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                        UUID.randomUUID(), wsId, userId, "ADMIN"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidMembershipStatus() {
        UUID userId = insertUser("bad-mstatus@example.com");
        UUID wsId = insertWorkspace("bad-mstatus-ws");
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', ?)",
                        UUID.randomUUID(), wsId, userId, "BANNED"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectRemovedWithoutEndedAt() {
        UUID userId = insertUser("no-ended@example.com");
        UUID wsId = insertWorkspace("no-ended-ws");
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                + "VALUES (?, ?, ?, 'OWNER', 'REMOVED')",
                        UUID.randomUUID(), wsId, userId
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectEndedAtBeforeJoinedAt() {
        UUID userId = insertUser("bad-dates@example.com");
        UUID wsId = insertWorkspace("bad-dates-ws");
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO workspace_membership "
                                + "(id, workspace_id, user_id, role, status, joined_at, ended_at) "
                                + "VALUES (?, ?, ?, 'OWNER', 'REMOVED', ?, ?)",
                        UUID.randomUUID(), wsId, userId,
                        OffsetDateTime.of(2026, 6, 7, 12, 0, 0, 0, ZoneOffset.UTC),
                        OffsetDateTime.of(2026, 6, 6, 12, 0, 0, 0, ZoneOffset.UTC)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDeleteWorkspaceWithMembership() {
        UUID userId = insertUser("del-ws@example.com");
        UUID wsId = insertWorkspace("del-ws-test");
        insertOwnerMembership(wsId, userId);

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM workspace WHERE id = ?", wsId)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDeleteUserWithMembership() {
        UUID userId = insertUser("del-user-ws@example.com");
        UUID wsId = insertWorkspace("del-user-ws-test");
        insertOwnerMembership(wsId, userId);

        // Must delete memberships first
        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM app_user WHERE id = ?", userId)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── audit_event ───────────────────────────────────────────────────────

    @Test
    void shouldInsertValidAuditEvent() {
        UUID userId = insertUser("audit-actor@example.com");
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_id, actor_type, action, summary) "
                        + "VALUES (?, ?, 'USER', 'USER_REGISTERED', 'User registered')",
                eventId, userId
        );
        assertThat(jdbc.queryForObject(
                "SELECT action FROM audit_event WHERE id = ?", String.class, eventId
        )).isEqualTo("USER_REGISTERED");
    }

    @Test
    void shouldInsertAuditEventWithoutWorkspace() {
        // Identity events (registration, login) happen before workspace exists
        UUID userId = insertUser("no-ws-audit@example.com");
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_id, actor_type, action, summary, workspace_id) "
                        + "VALUES (?, ?, 'USER', 'USER_LOGIN_SUCCESS', 'Login', NULL)",
                eventId, userId
        );
        assertThat(jdbc.queryForObject(
                "SELECT workspace_id FROM audit_event WHERE id = ?", String.class, eventId
        )).isNull();
    }

    @Test
    void shouldRejectInvalidActorType() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO audit_event (id, actor_type, action, summary) "
                                + "VALUES (?, ?, 'USER_REGISTERED', 'Test')",
                        UUID.randomUUID(), "EXTERNAL"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidSource() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO audit_event (id, actor_type, action, summary, source) "
                                + "VALUES (?, 'SYSTEM', 'TEST', 'Test', ?)",
                        UUID.randomUUID(), "WEBHOOK"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectUserActorWithoutActorId() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO audit_event (id, actor_type, action, summary) "
                                + "VALUES (?, 'USER', 'USER_REGISTERED', 'No actor')",
                        UUID.randomUUID()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptSystemActorWithoutActorId() {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, action, summary) "
                        + "VALUES (?, 'SYSTEM', 'SYSTEM_START', 'System started')",
                eventId
        );
        assertThat(jdbc.queryForObject(
                "SELECT actor_type FROM audit_event WHERE id = ?", String.class, eventId
        )).isEqualTo("SYSTEM");
    }

    @Test
    void shouldRejectNonExistentActorUser() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO audit_event (id, actor_id, actor_type, action, summary) "
                                + "VALUES (?, ?, 'USER', 'TEST', 'Bad actor')",
                        UUID.randomUUID(), UUID.randomUUID()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectNonExistentWorkspaceRef() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO audit_event (id, actor_type, action, summary, workspace_id) "
                                + "VALUES (?, 'SYSTEM', 'TEST', 'Bad ws', ?)",
                        UUID.randomUUID(), UUID.randomUUID()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectUpdateAuditEvent() {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, action, summary) "
                        + "VALUES (?, 'SYSTEM', 'TEST_UPDATE', 'Original')",
                eventId
        );

        assertThatThrownBy(() ->
                jdbc.update(
                        "UPDATE audit_event SET summary = 'Modified' WHERE id = ?", eventId
                )
        ).isInstanceOf(DataAccessException.class);

        // Verify data was NOT modified
        String summary = jdbc.queryForObject(
                "SELECT summary FROM audit_event WHERE id = ?", String.class, eventId
        );
        assertThat(summary).isEqualTo("Original");
    }

    @Test
    void shouldRejectDeleteAuditEvent() {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, action, summary) "
                        + "VALUES (?, 'SYSTEM', 'TEST_DELETE', 'ToDelete')",
                eventId
        );

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM audit_event WHERE id = ?", eventId)
        ).isInstanceOf(DataAccessException.class);

        // Verify row still exists
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE id = ?", Integer.class, eventId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldAllowInsertAfterFailedUpdate() {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, action, summary) "
                        + "VALUES (?, 'SYSTEM', 'TEST_INSERT_AFTER', 'First')",
                eventId
        );

        try {
            jdbc.update("UPDATE audit_event SET summary = 'Hack' WHERE id = ?", eventId);
        } catch (Exception ignored) {
            // Expected: AUDIT_EVENT_IMMUTABLE
        }

        // Insert a new event should still work
        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, action, summary) "
                        + "VALUES (?, 'SYSTEM', 'AFTER_FAIL', 'Still works')",
                newId
        );
        assertThat(jdbc.queryForObject(
                "SELECT action FROM audit_event WHERE id = ?", String.class, newId
        )).isEqualTo("AFTER_FAIL");
    }
}
