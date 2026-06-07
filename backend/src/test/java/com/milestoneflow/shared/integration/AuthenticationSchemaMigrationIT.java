package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Flyway migration execution and schema structure.
 * Verifies V001–V005 migrated successfully and the expected tables/columns exist.
 */
class AuthenticationSchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Flyway History ────────────────────────────────────────────────────

    @Nested
    class FlywayHistory {

        @Test
        void shouldHaveAllMigrationsApplied() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IN ('1','2','3','4','5')",
                    Integer.class
            );
            assertThat(count).isEqualTo(5);
        }

        @Test
        void shouldHaveNoFailedMigrations() {
            Integer failed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                    Integer.class
            );
            assertThat(failed).isZero();
        }

        @Test
        void shouldHaveV001Bootstrap() {
            assertThat(versionExists("1")).isTrue();
        }

        @Test
        void shouldHaveV002Identity() {
            assertThat(versionExists("2")).isTrue();
        }

        @Test
        void shouldHaveV003Workspace() {
            assertThat(versionExists("3")).isTrue();
        }

        @Test
        void shouldHaveV004Audit() {
            assertThat(versionExists("4")).isTrue();
        }

        @Test
        void shouldHaveV005Indexes() {
            assertThat(versionExists("5")).isTrue();
        }

        private boolean versionExists(String version) {
            // Flyway stores version as VARCHAR; check multiple possible formats.
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                    Integer.class, version
            );
            return count != null && count > 0;
        }
    }

    // ── Table Existence ───────────────────────────────────────────────────

    @Nested
    class TableExistence {

        @Test
        void shouldHaveAppUserTable() {
            assertThat(tableExists("app_user")).isTrue();
        }

        @Test
        void shouldHaveAuthSessionTable() {
            assertThat(tableExists("auth_session")).isTrue();
        }

        @Test
        void shouldHaveVerificationTokenTable() {
            assertThat(tableExists("verification_token")).isTrue();
        }

        @Test
        void shouldHaveWorkspaceTable() {
            assertThat(tableExists("workspace")).isTrue();
        }

        @Test
        void shouldHaveWorkspaceMembershipTable() {
            assertThat(tableExists("workspace_membership")).isTrue();
        }

        @Test
        void shouldHaveAuditEventTable() {
            assertThat(tableExists("audit_event")).isTrue();
        }

        @Test
        void shouldNotHaveBusinessTablesYet() {
            assertThat(tableExists("project")).isFalse();
            assertThat(tableExists("milestone")).isFalse();
            assertThat(tableExists("client")).isFalse();
            assertThat(tableExists("task")).isFalse();
            assertThat(tableExists("action_item_projection")).isFalse();
            assertThat(tableExists("feedback_projection")).isFalse();
            assertThat(tableExists("receivable")).isFalse();
        }

        private boolean tableExists(String tableName) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, tableName
            );
            return count != null && count > 0;
        }
    }

    // ── Column Types ──────────────────────────────────────────────────────

    @Nested
    class ColumnTypes {

        @Test
        void appUserPkShouldBeUuid() {
            assertThat(getColumnType("app_user", "id")).isEqualTo("uuid");
        }

        @Test
        void appUserEmailShouldBeVarchar() {
            String type = getColumnType("app_user", "email");
            assertThat(type).isEqualTo("varchar");
        }

        @Test
        void appUserCreatedAtShouldBeTimestamptz() {
            assertThat(getColumnType("app_user", "created_at"))
                    .isEqualTo("timestamptz");
        }

        @Test
        void appUserStatusShouldBeVarchar() {
            assertThat(getColumnType("app_user", "status"))
                    .isEqualTo("varchar");
        }

        @Test
        void appUserVersionShouldBeBigint() {
            assertThat(getColumnType("app_user", "version")).isEqualTo("bigint");
        }

        @Test
        void authSessionPkShouldBeUuid() {
            assertThat(getColumnType("auth_session", "id")).isEqualTo("uuid");
        }

        @Test
        void authSessionTokenHashesShouldBeVarchar() {
            assertThat(getColumnType("auth_session", "access_token_hash"))
                    .isEqualTo("varchar");
            assertThat(getColumnType("auth_session", "refresh_token_hash"))
                    .isEqualTo("varchar");
        }

        @Test
        void workspaceSettingsShouldBeJsonb() {
            assertThat(getColumnType("workspace", "settings")).isEqualTo("jsonb");
        }

        @Test
        void workspaceCurrencyShouldBeChar3() {
            Map<String, Object> col = jdbc.queryForMap(
                    "SELECT data_type, character_maximum_length FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'workspace' AND column_name = 'default_currency'"
            );
            assertThat(col.get("data_type")).isEqualTo("character");
            assertThat(col.get("character_maximum_length")).isEqualTo(3);
        }

        @Test
        void auditEventCreatedAtShouldBeTimestamptz() {
            assertThat(getColumnType("audit_event", "created_at"))
                    .isEqualTo("timestamptz");
        }

        @Test
        void appUserShouldNotHaveAutoIncrement() {
            String columnDefault = jdbc.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'app_user' AND column_name = 'id'",
                    String.class
            );
            assertThat(columnDefault).isNull();
        }

        @Test
        void authSessionShouldNotHaveAutoIncrement() {
            String columnDefault = jdbc.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'auth_session' AND column_name = 'id'",
                    String.class
            );
            assertThat(columnDefault).isNull();
        }

        @Test
        void appUserShouldNotHaveTokenPlaintextColumns() {
            List<String> columns = getColumnNames("app_user");
            assertThat(columns).doesNotContain("access_token", "refresh_token", "password",
                    "cookie", "authorization_header");
        }

        @Test
        void authSessionShouldNotHaveTokenPlaintextColumns() {
            List<String> columns = getColumnNames("auth_session");
            assertThat(columns).doesNotContain("access_token", "refresh_token", "password",
                    "cookie", "authorization_header");
        }

        @Test
        void auditEventShouldNotHaveSensitiveColumns() {
            List<String> columns = getColumnNames("audit_event");
            assertThat(columns).doesNotContain("password", "password_hash", "access_token",
                    "refresh_token", "access_token_hash", "refresh_token_hash",
                    "cookie", "authorization_header", "csrf_token");
        }

        @Test
        void auditEventShouldNotHaveUpdatedAt() {
            List<String> columns = getColumnNames("audit_event");
            assertThat(columns).doesNotContain("updated_at");
        }

        @Test
        void auditEventShouldNotHaveDeletedAt() {
            List<String> columns = getColumnNames("audit_event");
            assertThat(columns).doesNotContain("deleted_at");
        }

        @Test
        void auditEventShouldNotHaveVersion() {
            List<String> columns = getColumnNames("audit_event");
            assertThat(columns).doesNotContain("version");
        }

        private String getColumnType(String tableName, String columnName) {
            return jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                    String.class, tableName, columnName
            );
        }

        private List<String> getColumnNames(String tableName) {
            return jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                    String.class, tableName
            );
        }
    }
}
