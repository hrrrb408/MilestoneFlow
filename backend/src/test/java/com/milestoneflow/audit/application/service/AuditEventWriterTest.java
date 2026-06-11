package com.milestoneflow.audit.application.service;

import com.milestoneflow.audit.application.port.out.AuditEventRepository;
import com.milestoneflow.audit.domain.model.AuditEvent;
import com.milestoneflow.shared.id.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuditEventWriter}.
 *
 * <p>Validates that audit events are written correctly, metadata is sanitized,
 * and audit failures do not propagate to callers.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventWriterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-08T12:00:00Z");

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private IdGenerator idGenerator;

    private AuditEventWriter writer;

    @Captor
    private ArgumentCaptor<AuditEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, java.time.ZoneOffset.UTC);
        writer = new AuditEventWriter(auditEventRepository, idGenerator, clock);
    }

    private UUID userId() {
        return UUID.randomUUID();
    }

    private UUID setupIdGenerator() {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.when(idGenerator.nextId()).thenReturn(id);
        return id;
    }

    // ── writeUserEvent ───────────────────────────────────────────────────

    @Nested
    class WriteUserEvent {

        @Test
        void shouldWriteLoginSuccessEvent() {
            UUID userId = userId();
            UUID sessionId = UUID.randomUUID();
            UUID expectedId = setupIdGenerator();

            writer.writeUserEvent("AUTH_LOGIN_SUCCEEDED", userId,
                    "auth_session", sessionId, "req-123",
                    "User logged in successfully", Map.of("result", "success"));

            verify(auditEventRepository).save(eventCaptor.capture());
            AuditEvent event = eventCaptor.getValue();

            assertThat(event.getId()).isEqualTo(expectedId);
            assertThat(event.getActorId()).isEqualTo(userId);
            assertThat(event.getActorType()).isEqualTo("USER");
            assertThat(event.getAction()).isEqualTo("AUTH_LOGIN_SUCCEEDED");
            assertThat(event.getTargetType()).isEqualTo("auth_session");
            assertThat(event.getTargetId()).isEqualTo(sessionId);
            assertThat(event.getRequestId()).isEqualTo("req-123");
            assertThat(event.getSource()).isEqualTo("API");
            assertThat(event.getMetadata()).containsEntry("result", "success");
        }

        @Test
        void shouldWriteLoginFailureEvent() {
            UUID userId = userId();
            setupIdGenerator();

            writer.writeUserEvent("AUTH_LOGIN_FAILED", userId,
                    "app_user", userId, "req-456",
                    "Login failed: invalid credentials", Map.of("reasonCode", "invalid_credentials"));

            verify(auditEventRepository).save(eventCaptor.capture());
            AuditEvent event = eventCaptor.getValue();

            assertThat(event.getAction()).isEqualTo("AUTH_LOGIN_FAILED");
            assertThat(event.getMetadata()).containsEntry("reasonCode", "invalid_credentials");
        }

        @Test
        void shouldWriteRefreshReplayEvent() {
            UUID userId = userId();
            UUID familyId = UUID.randomUUID();
            setupIdGenerator();

            writer.writeUserEvent("AUTH_REFRESH_REPLAY_DETECTED", userId,
                    "auth_session", familyId, "req-789",
                    "Refresh replay detected — family revoked",
                    Map.of("reasonCode", "replay_detected", "sessionFamilyId", familyId.toString()));

            verify(auditEventRepository).save(eventCaptor.capture());
            AuditEvent event = eventCaptor.getValue();

            assertThat(event.getAction()).isEqualTo("AUTH_REFRESH_REPLAY_DETECTED");
            assertThat(event.getMetadata()).containsEntry("sessionFamilyId", familyId.toString());
        }

        @Test
        void shouldWritePasswordResetRequestedEvent() {
            UUID userId = userId();
            setupIdGenerator();

            writer.writeUserEvent("AUTH_PASSWORD_RESET_REQUESTED", userId,
                    "app_user", userId, "req-reset-1",
                    "Password reset requested", null);

            verify(auditEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getAction())
                    .isEqualTo("AUTH_PASSWORD_RESET_REQUESTED");
            assertThat(eventCaptor.getValue().getMetadata()).isNull();
        }

        @Test
        void shouldNotThrowWhenRawTokenInMetadata() {
            // Writer is best-effort: sensitive metadata causes IllegalArgumentException
            // inside AuditEvent.create, which the writer catches and logs.
            UUID userId = userId();
            setupIdGenerator();

            assertThatCode(() -> writer.writeUserEvent("AUTH_LOGIN_FAILED", userId,
                    "app_user", userId, "req-1",
                    "Login failed", Map.of("rawToken", "secret")))
                    .doesNotThrowAnyException();
            // Sensitive data protection is enforced at AuditEvent level (see AuditEventTest)
        }

        @Test
        void shouldNotThrowWhenPasswordInMetadata() {
            UUID userId = userId();
            setupIdGenerator();

            assertThatCode(() -> writer.writeUserEvent("AUTH_PASSWORD_CHANGE_FAILED", userId,
                    "app_user", userId, "req-1",
                    "Password change failed", Map.of("password", "old_pass")))
                    .doesNotThrowAnyException();
            // Sensitive data protection is enforced at AuditEvent level (see AuditEventTest)
        }
    }

    // ── writeSystemEvent ─────────────────────────────────────────────────

    @Nested
    class WriteSystemEvent {

        @Test
        void shouldWriteSystemEventWithoutActorId() {
            setupIdGenerator();

            writer.writeSystemEvent("AUTH_RATE_LIMIT_REJECTED",
                    "auth_endpoint", null, "req-rl-1",
                    "Rate limit exceeded", Map.of("reasonCode", "too_many_attempts"));

            verify(auditEventRepository).save(eventCaptor.capture());
            AuditEvent event = eventCaptor.getValue();

            assertThat(event.getActorId()).isNull();
            assertThat(event.getActorType()).isEqualTo("SYSTEM");
            assertThat(event.getAction()).isEqualTo("AUTH_RATE_LIMIT_REJECTED");
        }
    }

    // ── Failure Handling ─────────────────────────────────────────────────

    @Nested
    class FailureHandling {

        @Test
        void auditFailureShouldNotPropagate() {
            setupIdGenerator();
            doThrow(new RuntimeException("DB connection lost"))
                    .when(auditEventRepository).save(any());

            // Should not throw
            assertThatCode(() -> writer.writeUserEvent("AUTH_LOGIN_SUCCEEDED", userId(),
                    "auth_session", UUID.randomUUID(), "req-1",
                    "Login succeeded", null))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldPassRequestIdCorrectly() {
            setupIdGenerator();

            writer.writeUserEvent("AUTH_LOGOUT_SUCCEEDED", userId(),
                    "auth_session", UUID.randomUUID(), "my-request-id",
                    "User logged out", null);

            verify(auditEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getRequestId()).isEqualTo("my-request-id");
        }

        @Test
        void shouldPassActorIdCorrectly() {
            UUID actorId = userId();
            setupIdGenerator();

            writer.writeUserEvent("AUTH_LOGOUT_ALL_SUCCEEDED", actorId,
                    "app_user", actorId, "req-1",
                    "Logged out all sessions", null);

            verify(auditEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getActorId()).isEqualTo(actorId);
        }
    }
}
