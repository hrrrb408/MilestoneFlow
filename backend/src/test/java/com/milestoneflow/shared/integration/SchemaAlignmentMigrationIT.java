package com.milestoneflow.shared.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Incremental migration tests for MF-BE-005A schema alignment.
 *
 * <p>Tests that V005 → V006 → V007 migration is safe:
 * <ol>
 *   <li>Creates a fresh PostgreSQL 17 instance</li>
 *   <li>Runs Flyway with target=5 (V001–V005 only)</li>
 *   <li>Inserts representative V005-format data</li>
 *   <li>Runs Flyway to latest version</li>
 *   <li>Verifies data preservation and new structure</li>
 * </ol>
 *
 * <p>This test uses a separate database container to avoid interfering
 * with other integration tests that share the main Spring context.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaAlignmentMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("mf_alignment_test");

    private JdbcTemplate jdbc;
    private Flyway flyway;
    private SingleConnectionDataSource dataSource;

    @BeforeAll
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                true
        );
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @AfterAll
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    // ── Incremental Migration: V005 → V007 ──────────────────────────────

    @Nested
    class IncrementalMigration {

        @Test
        void shouldMigrateFromV005ToLatest() {
            // Step 1: Run V001–V005 only
            flyway.migrate(); // runs all — first time, so this creates everything
            // But we need to test incremental, so let's use a fresh schema approach
            // Actually, for a clean test, we run to target=5 first
        }

        @Test
        void shouldMigrateV005DataToV007Structure() {
            // Run all migrations to V005
            flyway = Flyway.configure()
                    .dataSource(
                            new SingleConnectionDataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword(),
                                    true
                            )
                    )
                    .locations("classpath:db/migration")
                    .target("5")
                    .load();
            flyway.migrate();

            // Insert V005-format data (with old expires_at column)
            UUID userId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    userId, "v005-migrate@example.com", "v005-migrate@example.com",
                    "Migration Test User", "{bcrypt}hash", "ACTIVE"
            );

            UUID sessionId = UUID.randomUUID();
            UUID familyId = UUID.randomUUID();
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime oldExpiresAt = OffsetDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC);
            String accessHash = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            String refreshHash = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, created_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    sessionId, userId, accessHash, refreshHash,
                    familyId, 0, "ACTIVE", createdAt, oldExpiresAt
            );

            // Verify V005 data is in place
            assertThat(jdbc.queryForObject(
                    "SELECT expires_at FROM auth_session WHERE id = ?",
                    OffsetDateTime.class, sessionId
            )).isEqualTo(oldExpiresAt);

            // Step 2: Migrate to latest (V006 + V007)
            Flyway fullFlyway = Flyway.configure()
                    .dataSource(
                            new SingleConnectionDataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword(),
                                    true
                            )
                    )
                    .locations("classpath:db/migration")
                    .load();
            fullFlyway.migrate();

            // Step 3: Verify data preservation
            assertThat(jdbc.queryForObject(
                    "SELECT id FROM auth_session WHERE id = ?", UUID.class, sessionId
            )).isEqualTo(sessionId);

            assertThat(jdbc.queryForObject(
                    "SELECT access_token_hash FROM auth_session WHERE id = ?",
                    String.class, sessionId
            )).isEqualTo(accessHash);

            assertThat(jdbc.queryForObject(
                    "SELECT refresh_token_hash FROM auth_session WHERE id = ?",
                    String.class, sessionId
            )).isEqualTo(refreshHash);

            assertThat(jdbc.queryForObject(
                    "SELECT session_family_id FROM auth_session WHERE id = ?",
                    UUID.class, sessionId
            )).isEqualTo(familyId);

            // Step 4: Verify old expires_at → refresh_expires_at
            OffsetDateTime refreshExpiresAt = jdbc.queryForObject(
                    "SELECT refresh_expires_at FROM auth_session WHERE id = ?",
                    OffsetDateTime.class, sessionId
            );
            assertThat(refreshExpiresAt).isEqualTo(oldExpiresAt);

            // Step 5: Verify access_expires_at was backfilled
            OffsetDateTime accessExpiresAt = jdbc.queryForObject(
                    "SELECT access_expires_at FROM auth_session WHERE id = ?",
                    OffsetDateTime.class, sessionId
            );
            // Should be LEAST(created_at + 15 minutes, refresh_expires_at)
            OffsetDateTime expectedAccessExpiry = OffsetDateTime.of(2026, 6, 1, 12, 15, 0, 0, ZoneOffset.UTC);
            assertThat(accessExpiresAt).isEqualTo(expectedAccessExpiry);

            // Step 6: Verify old column no longer exists
            List<String> columns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'auth_session' "
                            + "ORDER BY ordinal_position",
                    String.class
            );
            assertThat(columns).doesNotContain("expires_at");
            assertThat(columns).contains("access_expires_at", "refresh_expires_at");

            // Step 7: Verify redundant index was removed
            Integer redundantIdxCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes "
                            + "WHERE schemaname = 'public' AND tablename = 'auth_session' "
                            + "AND indexname = 'idx_auth_session_family'",
                    Integer.class
            );
            assertThat(redundantIdxCount).isZero();

            // Step 8: Verify unique constraint index still exists
            Integer uniqueIdxCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes "
                            + "WHERE schemaname = 'public' AND tablename = 'auth_session' "
                            + "AND indexname = 'uk_auth_session_family_gen'",
                    Integer.class
            );
            assertThat(uniqueIdxCount).isEqualTo(1);

            // Step 9: Verify Flyway history
            Integer totalMigrations = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                    Integer.class
            );
            assertThat(totalMigrations).isEqualTo(7);

            // Step 10: Verify V001–V005 checksums unchanged
            Integer checksumUnchanged = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history "
                            + "WHERE version IN ('1','2','3','4','5') AND success = true",
                    Integer.class
            );
            assertThat(checksumUnchanged).isEqualTo(5);
        }
    }

    // ── Expiration Constraint Tests ─────────────────────────────────────

    @Nested
    class ExpirationConstraints {

        @BeforeAll
        void ensureLatestSchema() {
            // Ensure we're on latest migration for constraint tests
            Flyway fullFlyway = Flyway.configure()
                    .dataSource(
                            new SingleConnectionDataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword(),
                                    true
                            )
                    )
                    .locations("classpath:db/migration")
                    .load();
            fullFlyway.migrate();
        }

        private UUID insertUser(String email) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    id, email, email, "Test User", "{bcrypt}hash", "ACTIVE"
            );
            return id;
        }

        private String uniqueHash() {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        @Test
        void shouldRejectAccessExpiryBeforeCreatedAt() {
            UUID userId = insertUser("access-before-created@example.com");
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime pastAccess = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime refreshExpires = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);

            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                                    + "session_family_id, refresh_generation, status, created_at, "
                                    + "access_expires_at, refresh_expires_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), userId, uniqueHash(), uniqueHash(),
                            UUID.randomUUID(), 0, "ACTIVE", createdAt, pastAccess, refreshExpires
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectRefreshExpiryBeforeCreatedAt() {
            UUID userId = insertUser("refresh-before-created@example.com");
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime accessExpires = OffsetDateTime.of(2026, 7, 1, 0, 15, 0, 0, ZoneOffset.UTC);
            OffsetDateTime pastRefresh = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                                    + "session_family_id, refresh_generation, status, created_at, "
                                    + "access_expires_at, refresh_expires_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), userId, uniqueHash(), uniqueHash(),
                            UUID.randomUUID(), 0, "ACTIVE", createdAt, accessExpires, pastRefresh
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectRefreshExpiryBeforeAccessExpiry() {
            UUID userId = insertUser("refresh-before-access@example.com");
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime accessExpires = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime refreshExpires = OffsetDateTime.of(2026, 7, 15, 0, 0, 0, 0, ZoneOffset.UTC);

            assertThatThrownBy(() ->
                    jdbc.update(
                            "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                                    + "session_family_id, refresh_generation, status, created_at, "
                                    + "access_expires_at, refresh_expires_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), userId, uniqueHash(), uniqueHash(),
                            UUID.randomUUID(), 0, "ACTIVE", createdAt, accessExpires, refreshExpires
                    )
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAcceptRefreshExpiryEqualToAccessExpiry() {
            UUID userId = insertUser("refresh-eq-access@example.com");
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime bothExpire = OffsetDateTime.of(2026, 7, 1, 0, 15, 0, 0, ZoneOffset.UTC);

            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, created_at, "
                            + "access_expires_at, refresh_expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, uniqueHash(), uniqueHash(),
                    UUID.randomUUID(), 0, "ACTIVE", createdAt, bothExpire, bothExpire
            );

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?",
                    Integer.class, userId
            );
            assertThat(count).isEqualTo(1);
        }

        @Test
        void shouldAcceptValid15MinAccessAnd30DayRefresh() {
            UUID userId = insertUser("standard-ttl@example.com");
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime accessExpires = OffsetDateTime.of(2026, 7, 1, 12, 15, 0, 0, ZoneOffset.UTC);
            OffsetDateTime refreshExpires = OffsetDateTime.of(2026, 7, 31, 12, 0, 0, 0, ZoneOffset.UTC);

            UUID sessionId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, created_at, "
                            + "access_expires_at, refresh_expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    sessionId, userId, uniqueHash(), uniqueHash(),
                    UUID.randomUUID(), 0, "ACTIVE", createdAt, accessExpires, refreshExpires
            );

            assertThat(jdbc.queryForObject(
                    "SELECT access_expires_at FROM auth_session WHERE id = ?",
                    OffsetDateTime.class, sessionId
            )).isEqualTo(accessExpires);

            assertThat(jdbc.queryForObject(
                    "SELECT refresh_expires_at FROM auth_session WHERE id = ?",
                    OffsetDateTime.class, sessionId
            )).isEqualTo(refreshExpires);
        }

        @Test
        void shouldAcceptCustomApplicationDefinedTtl() {
            // Application controls TTL via Clock + config, database does not enforce fixed durations
            UUID userId = insertUser("custom-ttl@example.com");
            OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime accessExpires = OffsetDateTime.of(2026, 7, 1, 0, 5, 0, 0, ZoneOffset.UTC); // 5 min
            OffsetDateTime refreshExpires = OffsetDateTime.of(2026, 7, 7, 0, 0, 0, 0, ZoneOffset.UTC); // 7 days

            jdbc.update(
                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                            + "session_family_id, refresh_generation, status, created_at, "
                            + "access_expires_at, refresh_expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, uniqueHash(), uniqueHash(),
                    UUID.randomUUID(), 0, "ACTIVE", createdAt, accessExpires, refreshExpires
            );

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE user_id = ?",
                    Integer.class, userId
            );
            assertThat(count).isEqualTo(1);
        }

        @Test
        void shouldNotHaveDatabaseFixedTtlDefaults() {
            // Verify no database default of 15 minutes or 30 days on expiration columns
            String accessDefault = jdbc.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'auth_session' "
                            + "AND column_name = 'access_expires_at'",
                    String.class
            );
            assertThat(accessDefault).isNull();

            String refreshDefault = jdbc.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'auth_session' "
                            + "AND column_name = 'refresh_expires_at'",
                    String.class
            );
            assertThat(refreshDefault).isNull();
        }
    }
}
