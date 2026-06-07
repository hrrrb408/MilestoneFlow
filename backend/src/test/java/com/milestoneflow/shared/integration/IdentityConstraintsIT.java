package com.milestoneflow.shared.integration;

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
 * Integration tests for V002 identity schema constraints.
 * Uses Testcontainers PostgreSQL 17 to verify real database behaviour.
 * All token hashes use UUID-based values to avoid collisions across
 * test classes sharing the same database context.
 */
class IdentityConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID insertUser(String emailNormalized) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, emailNormalized, emailNormalized, "Test User", "{bcrypt}hash", "ACTIVE"
        );
        return id;
    }

    private UUID insertUser(String emailNormalized, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, emailNormalized, emailNormalized, "Test User", "{bcrypt}hash", status
        );
        return id;
    }

    private UUID insertSession(UUID userId) {
        UUID id = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                        + "session_family_id, refresh_generation, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, id + "-access", id + "-refresh",
                familyId, 0, "ACTIVE", expires
        );
        return id;
    }

    private UUID insertVerificationToken(UUID userId, String purpose) {
        UUID id = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO verification_token (id, user_id, purpose, token_hash, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id, userId, purpose, id + "-token", expires
        );
        return id;
    }

    private String uniqueHash() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ── app_user ──────────────────────────────────────────────────────────

    @Test
    void shouldInsertValidUser() {
        UUID id = insertUser("valid@example.com");
        assertThat(jdbc.queryForObject(
                "SELECT email_normalized FROM app_user WHERE id = ?", String.class, id
        )).isEqualTo("valid@example.com");
    }

    @Test
    void shouldRejectDuplicateEmailNormalized() {
        insertUser("dup@example.com");
        assertThatThrownBy(() -> insertUser("dup@example.com"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectDifferentDisplayEmailSameNormalized() {
        // Application normalizes email before insert; database enforces uniqueness on email_normalized.
        // Simulate: same normalized email inserted twice with different display emails.
        jdbc.update(
                "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "User@Example.COM", "user@example.com",
                "User 1", "hash", "ACTIVE"
        );
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "user@example.com", "user@example.com",
                        "User 2", "hash", "ACTIVE"
                )
        ).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectInvalidStatus() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "bad-status@example.com", "bad-status@example.com",
                        "User", "hash", "INVALID_STATUS"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectNullPasswordHash() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO app_user (id, email, email_normalized, display_name, status) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "nopw@example.com", "nopw@example.com", "No Pw", "ACTIVE"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectNullEmailNormalized() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                                + "VALUES (?, ?, NULL, ?, ?, ?)",
                        UUID.randomUUID(), "null-norm@example.com", "User", "hash", "ACTIVE"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "neg@example.com", "neg@example.com",
                        "User", "hash", "ACTIVE", -1
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptPendingVerification() {
        UUID id = insertUser("pending@example.com", "PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM app_user WHERE id = ?", String.class, id
        )).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void shouldAcceptActive() {
        UUID id = insertUser("active@example.com", "ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM app_user WHERE id = ?", String.class, id
        )).isEqualTo("ACTIVE");
    }

    @Test
    void shouldAcceptDisabled() {
        UUID id = insertUser("disabled@example.com", "DISABLED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM app_user WHERE id = ?", String.class, id
        )).isEqualTo("DISABLED");
    }

    // ── auth_session ──────────────────────────────────────────────────────

    @Test
    void shouldInsertValidSession() {
        UUID userId = insertUser("session@example.com");
        UUID sessionId = insertSession(userId);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM auth_session WHERE id = ?", String.class, sessionId
        )).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectSessionWithNonExistentUser() {
        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), UUID.randomUUID(),
                    uniqueHash(), uniqueHash(), UUID.randomUUID(), 0, "ACTIVE", expires
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateAccessTokenHash() {
        UUID userId = insertUser("dup-access@example.com");
        insertSession(userId);
        String dupHash = jdbc.queryForObject(
                "SELECT access_token_hash FROM auth_session WHERE user_id = ?",
                String.class, userId
        );
        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId,
                    dupHash, uniqueHash(), UUID.randomUUID(), 0, "ACTIVE", expires
            );
        }).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectDuplicateRefreshTokenHash() {
        UUID userId = insertUser("dup-refresh@example.com");
        insertSession(userId);
        String dupHash = jdbc.queryForObject(
                "SELECT refresh_token_hash FROM auth_session WHERE user_id = ?",
                String.class, userId
        );
        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId,
                    uniqueHash(), dupHash, UUID.randomUUID(), 0, "ACTIVE", expires
            );
        }).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectDuplicateFamilyAndGeneration() {
        UUID userId = insertUser("fam-gen@example.com");
        UUID familyId = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        jdbc.update(
                "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                        + "session_family_id, refresh_generation, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), familyId, 0, "ACTIVE", expires
        );

        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                                + "session_family_id, refresh_generation, status, expires_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), familyId, 0, "ACTIVE", expires
                )
        ).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectNegativeGeneration() {
        UUID userId = insertUser("neg-gen@example.com");
        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), UUID.randomUUID(), -1, "ACTIVE", expires
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidSessionStatus() {
        UUID userId = insertUser("bad-session-status@example.com");
        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), UUID.randomUUID(), 0, "INVALID", expires
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectExpiresAtBeforeCreatedAt() {
        UUID userId = insertUser("bad-expiry@example.com");
        assertThatThrownBy(() -> {
            OffsetDateTime past = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, created_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), UUID.randomUUID(), 0, "ACTIVE",
                    OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC), past
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectRevokedWithoutRevokedAt() {
        UUID userId = insertUser("no-revoke-ts@example.com");
        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), UUID.randomUUID(), 0, "REVOKED", expires
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowMultipleGenerationsInSameFamily() {
        UUID userId = insertUser("multi-gen@example.com");
        UUID familyId = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        jdbc.update(
                "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                        + "session_family_id, refresh_generation, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), familyId, 0, "ACTIVE", expires
        );
        jdbc.update(
                "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                        + "session_family_id, refresh_generation, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), familyId, 1, "ACTIVE", expires
        );

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE session_family_id = ?",
                Integer.class, familyId
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldAllowDifferentFamiliesForSameUser() {
        UUID userId = insertUser("multi-fam@example.com");
        OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        jdbc.update(
                "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                        + "session_family_id, refresh_generation, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), UUID.randomUUID(), 0, "ACTIVE", expires
        );
        jdbc.update(
                "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                        + "session_family_id, refresh_generation, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, uniqueHash(), uniqueHash(), UUID.randomUUID(), 0, "ACTIVE", expires
        );

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session WHERE user_id = ?",
                Integer.class, userId
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldRejectDeleteUserWithSession() {
        UUID userId = insertUser("del-user-session@example.com");
        insertSession(userId);

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM app_user WHERE id = ?", userId)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── verification_token ─────────────────────────────────────────────────

    @Test
    void shouldInsertEmailVerificationToken() {
        UUID userId = insertUser("verify@example.com");
        UUID tokenId = insertVerificationToken(userId, "EMAIL_VERIFICATION");
        assertThat(jdbc.queryForObject(
                "SELECT purpose FROM verification_token WHERE id = ?", String.class, tokenId
        )).isEqualTo("EMAIL_VERIFICATION");
    }

    @Test
    void shouldInsertPasswordResetToken() {
        UUID userId = insertUser("reset@example.com");
        UUID tokenId = insertVerificationToken(userId, "PASSWORD_RESET");
        assertThat(jdbc.queryForObject(
                "SELECT purpose FROM verification_token WHERE id = ?", String.class, tokenId
        )).isEqualTo("PASSWORD_RESET");
    }

    @Test
    void shouldRejectInvalidPurpose() {
        UUID userId = insertUser("bad-purpose@example.com");
        assertThatThrownBy(() ->
                insertVerificationToken(userId, "INVALID_PURPOSE")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateTokenHash() {
        UUID userId = insertUser("dup-token@example.com");
        UUID tokenId = insertVerificationToken(userId, "EMAIL_VERIFICATION");
        String hash = jdbc.queryForObject(
                "SELECT token_hash FROM verification_token WHERE id = ?", String.class, tokenId
        );

        assertThatThrownBy(() -> {
            OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO verification_token (id, user_id, purpose, token_hash, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, "PASSWORD_RESET", hash, expires
            );
        }).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectTokenWithNonExistentUser() {
        assertThatThrownBy(() ->
                insertVerificationToken(UUID.randomUUID(), "EMAIL_VERIFICATION")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectTokenExpiresAtBeforeCreatedAt() {
        UUID userId = insertUser("bad-token-expiry@example.com");
        assertThatThrownBy(() -> {
            OffsetDateTime past = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            jdbc.update(
                    "INSERT INTO verification_token (id, user_id, purpose, token_hash, created_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, "EMAIL_VERIFICATION", uniqueHash(),
                    OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC), past
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDeleteUserWithVerificationToken() {
        UUID userId = insertUser("del-user-token@example.com");
        insertVerificationToken(userId, "EMAIL_VERIFICATION");

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM app_user WHERE id = ?", userId)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
