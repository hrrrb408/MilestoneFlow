package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AuthSessionRepository} against PostgreSQL 17.
 */
class AuthSessionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private static final Instant ACCESS_EXPIRES = Instant.parse("2026-06-01T12:15:00Z");
    private static final Instant REFRESH_EXPIRES = Instant.parse("2026-07-01T12:00:00Z");

    private UUID userId;

    @BeforeEach
    void setUp() {
        AppUser user = AppUser.create(UUID.randomUUID(), "session@example.test",
                "session@example.test", "Session User", "a".repeat(60), "zh-TW");
        appUserRepository.save(user);
        userId = user.getId();
    }

    private String hash(int seed) {
        return String.format("%064x", seed);
    }

    private AuthSession createSession(UUID userId, String accessHash, String refreshHash,
                                      UUID familyId, long generation) {
        return AuthSession.create(UUID.randomUUID(), userId, accessHash, refreshHash,
                familyId, generation, ACCESS_EXPIRES, REFRESH_EXPIRES, "TestAgent", "127.0.0.1");
    }

    // ── Save and retrieve ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Save and retrieve")
    class SaveAndRetrieve {

        @Test
        @DisplayName("should save and retrieve session by ID")
        void shouldSaveAndRetrieveById() {
            AuthSession session = createSession(userId, hash(1), hash(2), UUID.randomUUID(), 0);
            authSessionRepository.save(session);

            assertThat(authSessionRepository.findById(session.getId())).isPresent();
        }

        @Test
        @DisplayName("should persist all fields")
        void shouldPersistAllFields() {
            UUID familyId = UUID.randomUUID();
            AuthSession session = createSession(userId, hash(3), hash(4), familyId, 0);
            authSessionRepository.save(session);

            AuthSession loaded = authSessionRepository.findById(session.getId()).orElseThrow();

            assertThat(loaded.getUserId()).isEqualTo(userId);
            assertThat(loaded.getAccessTokenHash()).isEqualTo(hash(3));
            assertThat(loaded.getRefreshTokenHash()).isEqualTo(hash(4));
            assertThat(loaded.getSessionFamilyId()).isEqualTo(familyId);
            assertThat(loaded.getRefreshGeneration()).isEqualTo(0);
            assertThat(loaded.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
            assertThat(loaded.getUserAgent()).isEqualTo("TestAgent");
            assertThat(loaded.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(loaded.getAccessExpiresAt()).isEqualTo(ACCESS_EXPIRES);
            assertThat(loaded.getRefreshExpiresAt()).isEqualTo(REFRESH_EXPIRES);
            assertThat(loaded.getLastUsedAt()).isNull();
            assertThat(loaded.getRevokedAt()).isNull();
            assertThat(loaded.getRevokeReason()).isNull();
            assertThat(loaded.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should persist revoked state")
        void shouldPersistRevokedState() {
            AuthSession session = createSession(userId, hash(5), hash(6), UUID.randomUUID(), 0);
            Instant revokedAt = Instant.parse("2026-06-01T13:00:00Z");
            session.revoke(revokedAt, "user_logout");
            authSessionRepository.save(session);

            AuthSession loaded = authSessionRepository.findById(session.getId()).orElseThrow();

            assertThat(loaded.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
            assertThat(loaded.getRevokedAt()).isEqualTo(revokedAt);
            assertThat(loaded.getRevokeReason()).isEqualTo("user_logout");
        }
    }

    // ── Token hash queries ────────────────────────────────────────────────

    @Nested
    @DisplayName("Token hash queries")
    class TokenHashQueries {

        @Test
        @DisplayName("should find by access token hash")
        void shouldFindByAccessTokenHash() {
            AuthSession session = createSession(userId, hash(10), hash(11), UUID.randomUUID(), 0);
            authSessionRepository.save(session);

            assertThat(authSessionRepository.findByAccessTokenHash(hash(10))).isPresent();
            assertThat(authSessionRepository.findByAccessTokenHash("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("should find by refresh token hash")
        void shouldFindByRefreshTokenHash() {
            AuthSession session = createSession(userId, hash(12), hash(13), UUID.randomUUID(), 0);
            authSessionRepository.save(session);

            assertThat(authSessionRepository.findByRefreshTokenHash(hash(13))).isPresent();
            assertThat(authSessionRepository.findByRefreshTokenHash("nonexistent")).isEmpty();
        }
    }

    // ── User + Status query ───────────────────────────────────────────────

    @Nested
    @DisplayName("User and status queries")
    class UserStatusQueries {

        @Test
        @DisplayName("should find sessions by userId and status")
        void shouldFindByUserIdAndStatus() {
            AuthSession active = createSession(userId, hash(20), hash(21), UUID.randomUUID(), 0);
            AuthSession revoked = createSession(userId, hash(22), hash(23), UUID.randomUUID(), 0);
            revoked.revoke(Instant.parse("2026-06-01T13:00:00Z"), "test");
            authSessionRepository.save(active);
            authSessionRepository.save(revoked);

            List<AuthSession> activeSessions = authSessionRepository.findByUserIdAndStatus(
                    userId, AuthSessionStatus.ACTIVE);

            assertThat(activeSessions).hasSize(1);
            assertThat(activeSessions.get(0).getId()).isEqualTo(active.getId());
        }
    }

    // ── Family query ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Family queries")
    class FamilyQueries {

        @Test
        @DisplayName("should find sessions by session family ID")
        void shouldFindBySessionFamilyId() {
            UUID familyId = UUID.randomUUID();
            AuthSession gen0 = createSession(userId, hash(30), hash(31), familyId, 0);
            AuthSession gen1 = createSession(userId, hash(32), hash(33), familyId, 1);
            authSessionRepository.save(gen0);
            authSessionRepository.save(gen1);

            List<AuthSession> family = authSessionRepository.findBySessionFamilyId(familyId);

            assertThat(family).hasSize(2);
        }

        @Test
        @DisplayName("should persist generation correctly")
        void shouldPersistGeneration() {
            AuthSession session = createSession(userId, hash(34), hash(35), UUID.randomUUID(), 5);
            authSessionRepository.save(session);

            AuthSession loaded = authSessionRepository.findById(session.getId()).orElseThrow();
            assertThat(loaded.getRefreshGeneration()).isEqualTo(5);
        }
    }

    // ── Unique constraints ────────────────────────────────────────────────

    @Nested
    @DisplayName("Unique constraints")
    class UniqueConstraints {

        @Test
        @DisplayName("should reject duplicate access token hash")
        void shouldRejectDuplicateAccessTokenHash() {
            AuthSession s1 = createSession(userId, hash(40), hash(41), UUID.randomUUID(), 0);
            authSessionRepository.save(s1);

            AuthSession s2 = createSession(userId, hash(40), hash(42), UUID.randomUUID(), 0);

            assertThatThrownBy(() -> authSessionRepository.save(s2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should reject duplicate refresh token hash")
        void shouldRejectDuplicateRefreshTokenHash() {
            AuthSession s1 = createSession(userId, hash(43), hash(44), UUID.randomUUID(), 0);
            authSessionRepository.save(s1);

            AuthSession s2 = createSession(userId, hash(45), hash(44), UUID.randomUUID(), 0);

            assertThatThrownBy(() -> authSessionRepository.save(s2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should reject duplicate family + generation")
        void shouldRejectDuplicateFamilyGeneration() {
            UUID familyId = UUID.randomUUID();
            AuthSession s1 = createSession(userId, hash(46), hash(47), familyId, 0);
            authSessionRepository.save(s1);

            AuthSession s2 = createSession(userId, hash(48), hash(49), familyId, 0);

            assertThatThrownBy(() -> authSessionRepository.save(s2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── FK constraints ────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK constraints")
    class FkConstraints {

        @Test
        @DisplayName("should reject session with non-existent user")
        void shouldRejectNonExistentUser() {
            AuthSession session = createSession(UUID.randomUUID(), hash(50), hash(51),
                    UUID.randomUUID(), 0);

            assertThatThrownBy(() -> authSessionRepository.save(session))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ── No N+1 loading ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Object graph")
    class ObjectGraph {

        @Test
        @DisplayName("should not load AppUser entity when loading session")
        void shouldNotLoadAppUserEntity() {
            AuthSession session = createSession(userId, hash(60), hash(61), UUID.randomUUID(), 0);
            authSessionRepository.save(session);

            AuthSession loaded = authSessionRepository.findById(session.getId()).orElseThrow();

            // userId is a plain UUID, not a lazy-loaded entity
            assertThat(loaded.getUserId()).isInstanceOf(UUID.class);
            assertThat(loaded.getUserId()).isEqualTo(userId);
        }
    }
}
