package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for V004 audit_event constraints and append-only protection.
 * Uses Testcontainers PostgreSQL 17 to verify real database behaviour.
 */
class AuditConstraintsIT extends AbstractIntegrationTest {

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
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, "Test Workspace", "audit-ws-" + id.toString().substring(0, 8),
                "TWD", "Asia/Taipei", "ACTIVE", "{}", createdBy
        );
        return id;
    }

    private UUID insertAuditEvent(String actorType, UUID actorId, UUID workspaceId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, actor_id, action, target_type, target_id, "
                        + "workspace_id, source, summary, metadata, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, actorType, actorId, "USER_REGISTERED", "app_user", actorId,
                workspaceId, "API", "User registered", "{}",
                OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        return id;
    }

    private UUID insertAuditEvent() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_event (id, actor_type, action, source, summary, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "SYSTEM", "SYSTEM_STARTED", "INTERNAL", "System started",
                OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        );
        return id;
    }

    // ── Constraint Tests ──────────────────────────────────────────────────

    @Nested
    class ConstraintTests {

        @Test
        void shouldInsertValidAuditEventWithUserActor() {
            UUID userId = insertUser("audit-valid@example.com");
            UUID eventId = insertAuditEvent("USER", userId, null);
            assertThat(jdbc.queryForObject(
                    "SELECT actor_type FROM audit_event WHERE id = ?", String.class, eventId
            )).isEqualTo("USER");
        }

        @Test
        void shouldInsertSystemActorWithoutActorId() {
            UUID eventId = insertAuditEvent();
            assertThat(jdbc.queryForObject(
                    "SELECT actor_type FROM audit_event WHERE id = ?", String.class, eventId
            )).isEqualTo("SYSTEM");
        }

        @Test
        void shouldInsertJobActorWithoutActorId() {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO audit_event (id, actor_type, action, source, summary, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    id, "JOB", "CLEANUP", "CRON", "Scheduled cleanup",
                    OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            );
            assertThat(jdbc.queryForObject(
                    "SELECT actor_type FROM audit_event WHERE id = ?", String.class, id
            )).isEqualTo("JOB");
        }

        @Test
        void shouldRejectInvalidActorType() {
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO audit_event (id, actor_type, action, source, summary, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), "CLIENT", "ACTION", "API", "Bad actor",
                            OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectInvalidSource() {
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO audit_event (id, actor_type, action, source, summary, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), "SYSTEM", "ACTION", "INVALID_SOURCE", "Bad source",
                            OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectUserActorWithoutActorId() {
            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO audit_event (id, actor_type, action, source, summary, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), "USER", "LOGIN", "API", "User login",
                            OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAcceptMetadataAsJsonObject() {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO audit_event (id, actor_type, action, source, summary, metadata, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, "SYSTEM", "TEST", "INTERNAL", "Test",
                    "{\"ip\": \"127.0.0.1\", \"browser\": \"chrome\"}",
                    OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            );
            String metadata = jdbc.queryForObject(
                    "SELECT metadata::text FROM audit_event WHERE id = ?", String.class, id
            );
            assertThat(metadata).contains("ip");
        }

        @Test
        void shouldAcceptNullMetadata() {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO audit_event (id, actor_type, action, source, summary, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    id, "SYSTEM", "TEST", "INTERNAL", "Test with null metadata",
                    OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            );
            assertThat(jdbc.queryForObject(
                    "SELECT metadata FROM audit_event WHERE id = ?", String.class, id
            )).isNull();
        }

        @Test
        void shouldRejectNonExistentActorUserId() {
            assertThatThrownBy(() ->
                    insertAuditEvent("USER", UUID.randomUUID(), null)
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectNonExistentWorkspaceId() {
            UUID userId = insertUser("audit-bad-ws@example.com");
            assertThatThrownBy(() ->
                    insertAuditEvent("USER", userId, UUID.randomUUID())
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAcceptWorkspaceIdForWorkspaceEvents() {
            UUID userId = insertUser("audit-ws-event@example.com");
            UUID wsId = insertWorkspace(userId);
            UUID eventId = insertAuditEvent("USER", userId, wsId);
            assertThat(jdbc.queryForObject(
                    "SELECT workspace_id FROM audit_event WHERE id = ?", String.class, eventId
            )).isNotNull();
        }

        @Test
        void shouldAcceptNullWorkspaceForIdentityEvents() {
            UUID userId = insertUser("audit-no-ws@example.com");
            UUID eventId = insertAuditEvent("USER", userId, null);
            assertThat(jdbc.queryForObject(
                    "SELECT workspace_id FROM audit_event WHERE id = ?", String.class, eventId
            )).isNull();
        }

        @Test
        void shouldAcceptRequestId() {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO audit_event (id, actor_type, action, source, summary, request_id, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, "SYSTEM", "TEST", "INTERNAL", "With request ID",
                    UUID.randomUUID().toString(),
                    OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            );
            String requestId = jdbc.queryForObject(
                    "SELECT request_id FROM audit_event WHERE id = ?", String.class, id
            );
            assertThat(requestId).isNotNull();
        }

        @Test
        void occurredAtShouldUseTimestamptz() {
            String dataType = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'audit_event' AND column_name = 'created_at'",
                    String.class
            );
            assertThat(dataType).isEqualTo("timestamp with time zone");
        }
    }

    // ── Append-Only Protection ────────────────────────────────────────────

    @Nested
    class AppendOnlyProtection {

        @Test
        void shouldRejectUpdate() {
            UUID eventId = insertAuditEvent();
            assertThatThrownBy(() ->
                    jdbc.update("UPDATE audit_event SET summary = 'tampered' WHERE id = ?", eventId)
            ).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("AUDIT_EVENT_IMMUTABLE");
        }

        @Test
        void shouldRejectDelete() {
            UUID eventId = insertAuditEvent();
            assertThatThrownBy(() ->
                    jdbc.update("DELETE FROM audit_event WHERE id = ?", eventId)
            ).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("AUDIT_EVENT_IMMUTABLE");
        }

        @Test
        void shouldAllowInsertAfterFailedUpdate() {
            UUID eventId = insertAuditEvent();

            // UPDATE fails
            assertThatThrownBy(() ->
                    jdbc.update("UPDATE audit_event SET summary = 'tampered' WHERE id = ?", eventId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // INSERT should still work
            UUID newId = insertAuditEvent();
            assertThat(jdbc.queryForObject(
                    "SELECT id FROM audit_event WHERE id = ?", UUID.class, newId
            )).isEqualTo(newId);
        }

        @Test
        void shouldAllowInsertAfterFailedDelete() {
            UUID eventId = insertAuditEvent();

            // DELETE fails
            assertThatThrownBy(() ->
                    jdbc.update("DELETE FROM audit_event WHERE id = ?", eventId)
            ).isInstanceOf(DataIntegrityViolationException.class);

            // INSERT should still work
            UUID newId = insertAuditEvent();
            assertThat(jdbc.queryForObject(
                    "SELECT id FROM audit_event WHERE id = ?", UUID.class, newId
            )).isEqualTo(newId);
        }
    }

    // ── Structural Tests ──────────────────────────────────────────────────

    @Nested
    class StructuralTests {

        @Test
        void auditEventShouldNotHaveUpdatedAt() {
            List<String> columns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'audit_event' ORDER BY ordinal_position",
                    String.class
            );
            assertThat(columns).doesNotContain("updated_at");
        }

        @Test
        void auditEventShouldNotHaveDeletedAt() {
            List<String> columns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'audit_event' ORDER BY ordinal_position",
                    String.class
            );
            assertThat(columns).doesNotContain("deleted_at");
        }

        @Test
        void auditEventShouldNotHaveVersion() {
            List<String> columns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'audit_event' ORDER BY ordinal_position",
                    String.class
            );
            assertThat(columns).doesNotContain("version");
        }

        @Test
        void auditEventShouldNotContainSensitiveFields() {
            List<String> columns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'audit_event' ORDER BY ordinal_position",
                    String.class
            );
            assertThat(columns).doesNotContain(
                    "password", "password_hash", "access_token", "refresh_token",
                    "access_token_hash", "refresh_token_hash", "cookie",
                    "authorization_header", "csrf_token"
            );
        }
    }
}
