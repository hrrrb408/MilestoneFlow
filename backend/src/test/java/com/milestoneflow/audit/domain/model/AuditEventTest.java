package com.milestoneflow.audit.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuditEvent}.
 *
 * <p>Validates construction, nullability constraints, metadata sanitization,
 * and toString safety.
 */
class AuditEventTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-08T12:00:00Z");

    private UUID newId() {
        return UUID.randomUUID();
    }

    // ── Construction ─────────────────────────────────────────────────────

    @Nested
    class Construction {

        @Test
        void shouldCreateValidEventWithUserActor() {
            UUID actorId = newId();
            UUID targetId = newId();

            AuditEvent event = AuditEvent.create(
                    newId(), actorId, "USER", "AUTH_LOGIN_SUCCEEDED",
                    "auth_session", targetId, null, "req-123",
                    "API", "User logged in", null, FIXED_INSTANT
            );

            assertThat(event.getActorId()).isEqualTo(actorId);
            assertThat(event.getActorType()).isEqualTo("USER");
            assertThat(event.getAction()).isEqualTo("AUTH_LOGIN_SUCCEEDED");
            assertThat(event.getTargetType()).isEqualTo("auth_session");
            assertThat(event.getTargetId()).isEqualTo(targetId);
            assertThat(event.getWorkspaceId()).isNull();
            assertThat(event.getRequestId()).isEqualTo("req-123");
            assertThat(event.getSource()).isEqualTo("API");
            assertThat(event.getSummary()).isEqualTo("User logged in");
            assertThat(event.getMetadata()).isNull();
            assertThat(event.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        void shouldCreateSystemEventWithoutActorId() {
            AuditEvent event = AuditEvent.create(
                    newId(), null, "SYSTEM", "SYSTEM_STARTED",
                    null, null, null, null,
                    "INTERNAL", "System started", null, FIXED_INSTANT
            );

            assertThat(event.getActorId()).isNull();
            assertThat(event.getActorType()).isEqualTo("SYSTEM");
        }

        @Test
        void shouldAllowNullableWorkspaceId() {
            AuditEvent event = AuditEvent.create(
                    newId(), newId(), "USER", "AUTH_REGISTER_SUCCEEDED",
                    "app_user", newId(), null, "req-1",
                    "API", "User registered", null, FIXED_INSTANT
            );
            assertThat(event.getWorkspaceId()).isNull();
        }

        @Test
        void shouldAllowNullableRequestId() {
            AuditEvent event = AuditEvent.create(
                    newId(), null, "SYSTEM", "CRON_JOB_EXECUTED",
                    null, null, null, null,
                    "CRON", "Cleanup", null, FIXED_INSTANT
            );
            assertThat(event.getRequestId()).isNull();
        }

        @Test
        void shouldAllowNullableMetadata() {
            AuditEvent event = AuditEvent.create(
                    newId(), newId(), "USER", "AUTH_LOGOUT_SUCCEEDED",
                    "auth_session", newId(), null, "req-1",
                    "API", "User logged out", null, FIXED_INSTANT
            );
            assertThat(event.getMetadata()).isNull();
        }

        @Test
        void shouldRejectNullActorType() {
            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), null, null, "ACTION",
                    null, null, null, null,
                    "API", "Summary", null, FIXED_INSTANT
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("actorType");
        }

        @Test
        void shouldRejectNullAction() {
            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), null, "SYSTEM", null,
                    null, null, null, null,
                    "API", "Summary", null, FIXED_INSTANT
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("action");
        }

        @Test
        void shouldRejectNullSource() {
            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), null, "SYSTEM", "ACTION",
                    null, null, null, null,
                    null, "Summary", null, FIXED_INSTANT
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("source");
        }

        @Test
        void shouldRejectNullSummary() {
            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), null, "SYSTEM", "ACTION",
                    null, null, null, null,
                    "API", null, null, FIXED_INSTANT
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("summary");
        }

        @Test
        void shouldRejectNullCreatedAt() {
            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), null, "SYSTEM", "ACTION",
                    null, null, null, null,
                    "API", "Summary", null, null
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("createdAt");
        }
    }

    // ── Metadata Sanitization ────────────────────────────────────────────

    @Nested
    class MetadataSanitization {

        @Test
        void shouldAcceptSafeMetadata() {
            Map<String, Object> safe = Map.of(
                    "reasonCode", "invalid_credentials",
                    "result", "failed"
            );

            AuditEvent event = AuditEvent.create(
                    newId(), newId(), "USER", "AUTH_LOGIN_FAILED",
                    "app_user", newId(), null, "req-1",
                    "API", "Login failed", safe, FIXED_INSTANT
            );

            assertThat(event.getMetadata()).containsEntry("reasonCode", "invalid_credentials");
        }

        @Test
        void shouldRejectPasswordInMetadata() {
            Map<String, Object> bad = Map.of("password", "secret123");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("password");
        }

        @Test
        void shouldRejectRawTokenInMetadata() {
            Map<String, Object> bad = Map.of("rawToken", "abc123");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rawToken");
        }

        @Test
        void shouldRejectTokenHashInMetadata() {
            Map<String, Object> bad = Map.of("tokenHash", "sha256hash");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tokenHash");
        }

        @Test
        void shouldRejectCookieInMetadata() {
            Map<String, Object> bad = Map.of("cookie", "MF_ACCESS=abc");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cookie");
        }

        @Test
        void shouldRejectAuthorizationInMetadata() {
            Map<String, Object> bad = Map.of("authorization", "Bearer abc");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("authorization");
        }

        @Test
        void shouldRejectResetTokenInMetadata() {
            Map<String, Object> bad = Map.of("resetToken", "token123");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resetToken");
        }

        @Test
        void shouldRejectCaseInsensitive() {
            Map<String, Object> bad = Map.of("Password", "secret");

            assertThatThrownBy(() -> AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Bad metadata", bad, FIXED_INSTANT
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAllowAllowedMetadataKeys() {
            Map<String, Object> safe = Map.of(
                    "reasonCode", "replay_detected",
                    "ipMasked", "192.168.1.0",
                    "userAgentFamily", "Chrome",
                    "rateLimitKeyHash", "hash123",
                    "sessionFamilyId", UUID.randomUUID().toString()
            );

            AuditEvent event = AuditEvent.create(
                    newId(), newId(), "USER", "AUTH_REFRESH_REPLAY_DETECTED",
                    null, null, null, "req-1",
                    "API", "Replay detected", safe, FIXED_INSTANT
            );

            assertThat(event.getMetadata()).hasSize(5);
        }

        @Test
        void shouldProduceImmutableMetadataCopy() {
            Map<String, Object> meta = new java.util.HashMap<>(Map.of("reasonCode", "test"));
            AuditEvent event = AuditEvent.create(
                    newId(), newId(), "USER", "ACTION",
                    null, null, null, "req-1",
                    "API", "Test", meta, FIXED_INSTANT
            );

            // Modifying the original map should not affect the event
            meta.put("extra", "value");
            assertThat(event.getMetadata()).doesNotContainKey("extra");
        }
    }

    // ── toString Safety ──────────────────────────────────────────────────

    @Nested
    class ToStringSafety {

        @Test
        void toStringShouldNotContainMetadata() {
            Map<String, Object> meta = Map.of("reasonCode", "some-sensitive-detail");
            AuditEvent event = AuditEvent.create(
                    newId(), newId(), "USER", "AUTH_LOGIN_FAILED",
                    null, null, null, "req-1",
                    "API", "Login failed", meta, FIXED_INSTANT
            );

            String str = event.toString();
            assertThat(str).doesNotContain("some-sensitive-detail");
            assertThat(str).doesNotContain("metadata");
        }
    }
}
