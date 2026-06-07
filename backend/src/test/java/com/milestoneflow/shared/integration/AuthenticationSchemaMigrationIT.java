package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying V002–V005 migration execution,
 * table existence, column types, and index structure.
 */
class AuthenticationSchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Flyway migration history ──────────────────────────────────────────

    @Test
    void shouldHaveAllMigrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class
        );
        assertThat(versions).containsExactly("1", "2", "3", "4", "5");
    }

    // ── Table existence ───────────────────────────────────────────────────

    @Test
    void shouldHaveAllRequiredTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
                String.class
        );
        assertThat(tables).contains(
                "app_user", "auth_session", "verification_token",
                "workspace", "workspace_membership", "audit_event"
        );
    }

    @Test
    void shouldNotHaveBusinessTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                String.class
        );
        assertThat(tables).doesNotContain(
                "project", "milestone", "client", "task",
                "action_item_projection", "feedback_projection"
        );
    }

    // ── Column types ──────────────────────────────────────────────────────

    @Test
    void shouldUseUuidPrimaryKeyType() {
        assertColumnType("app_user", "id", "uuid");
        assertColumnType("auth_session", "id", "uuid");
        assertColumnType("verification_token", "id", "uuid");
        assertColumnType("workspace", "id", "uuid");
        assertColumnType("workspace_membership", "id", "uuid");
        assertColumnType("audit_event", "id", "uuid");
    }

    @Test
    void shouldUseTimestamptzForTimeColumns() {
        assertColumnType("app_user", "created_at", "timestamp with time zone");
        assertColumnType("app_user", "updated_at", "timestamp with time zone");
        assertColumnType("auth_session", "created_at", "timestamp with time zone");
        assertColumnType("auth_session", "expires_at", "timestamp with time zone");
        assertColumnType("audit_event", "created_at", "timestamp with time zone");
    }

    @Test
    void shouldUseJsonbForSettingsAndMetadata() {
        assertColumnType("workspace", "settings", "jsonb");
        assertColumnType("audit_event", "metadata", "jsonb");
    }

    @Test
    void shouldUseBigintForVersion() {
        assertColumnType("app_user", "version", "bigint");
        assertColumnType("workspace", "version", "bigint");
        assertColumnType("workspace_membership", "version", "bigint");
    }

    @Test
    void shouldNotHaveTokenPlaintextColumns() {
        List<String> sessionCols = getColumnNames("auth_session");
        assertThat(sessionCols).doesNotContain("access_token", "refresh_token", "cookie");

        List<String> tokenCols = getColumnNames("verification_token");
        assertThat(tokenCols).doesNotContain("token", "raw_token");
    }

    @Test
    void shouldNotHavePasswordPlaintextColumns() {
        List<String> userCols = getColumnNames("app_user");
        assertThat(userCols).contains("password_hash");
        assertThat(userCols).doesNotContain("password");
    }

    @Test
    void shouldNotHaveAutoIncrementColumns() {
        for (String table : List.of("app_user", "auth_session", "verification_token",
                "workspace", "workspace_membership", "audit_event")) {
            String defaultValue = jdbc.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_name = ? AND column_name = 'id'",
                    String.class, table
            );
            assertThat(defaultValue).isNull();
        }
    }

    @Test
    void auditEventShouldNotHaveUpdatedAtOrDeletedAt() {
        List<String> cols = getColumnNames("audit_event");
        assertThat(cols).doesNotContain("updated_at", "deleted_at");
    }

    @Test
    void auditEventShouldNotHaveSensitiveFields() {
        List<String> cols = getColumnNames("audit_event");
        assertThat(cols).doesNotContain(
                "password", "password_hash", "access_token", "refresh_token",
                "cookie", "authorization_header", "csrf_token"
        );
    }

    // ── Index verification ────────────────────────────────────────────────

    @Test
    void shouldHaveUniqueIndexOnEmailNormalized() {
        assertThat(findIndexColumns("uk_app_user_email_normalized"))
                .containsExactly("email_normalized");
    }

    @Test
    void shouldHaveUniqueIndexOnAccessTokenHash() {
        assertThat(findIndexColumns("uk_auth_session_access_hash"))
                .containsExactly("access_token_hash");
    }

    @Test
    void shouldHaveUniqueIndexOnRefreshTokenHash() {
        assertThat(findIndexColumns("uk_auth_session_refresh_hash"))
                .containsExactly("refresh_token_hash");
    }

    @Test
    void shouldHaveUniqueIndexOnSessionFamilyGen() {
        assertThat(findIndexColumns("uk_auth_session_family_gen"))
                .containsExactly("session_family_id", "refresh_generation");
    }

    @Test
    void shouldHavePartialUniqueIndexForActiveOwner() {
        Map<String, Object> idx = findIndex("uk_workspace_membership_active_owner");
        assertThat(idx).isNotNull();
        assertThat(idx.get("columns")).hasToString("[workspace_id]");

        String predicate = jdbc.queryForObject(
                "SELECT pg_get_expr(indpred, indrelid) FROM pg_index i "
                        + "JOIN pg_class c ON i.indexrelid = c.oid "
                        + "WHERE c.relname = 'uk_workspace_membership_active_owner'",
                String.class
        );
        assertThat(predicate).contains("OWNER", "ACTIVE");
    }

    @Test
    void shouldHavePartialUniqueIndexForActiveUser() {
        Map<String, Object> idx = findIndex("uk_workspace_membership_active_user");
        assertThat(idx).isNotNull();

        String predicate = jdbc.queryForObject(
                "SELECT pg_get_expr(indpred, indrelid) FROM pg_index i "
                        + "JOIN pg_class c ON i.indexrelid = c.oid "
                        + "WHERE c.relname = 'uk_workspace_membership_active_user'",
                String.class
        );
        assertThat(predicate).contains("ACTIVE");
    }

    @Test
    void shouldHaveAuthSessionUserStatusIndex() {
        assertThat(findIndexColumns("idx_auth_session_user_status"))
                .containsExactly("user_id", "status");
    }

    @Test
    void shouldHaveAuthSessionFamilyStatusIndex() {
        assertThat(findIndexColumns("idx_auth_session_family_status"))
                .containsExactly("session_family_id", "status");
    }

    @Test
    void shouldHaveVerificationTokenLookupIndex() {
        Map<String, Object> idx = findIndex("idx_verification_token_lookup");
        assertThat(idx).isNotNull();

        String predicate = jdbc.queryForObject(
                "SELECT pg_get_expr(indpred, indrelid) FROM pg_index i "
                        + "JOIN pg_class c ON i.indexrelid = c.oid "
                        + "WHERE c.relname = 'idx_verification_token_lookup'",
                String.class
        );
        assertThat(predicate).contains("used_at IS NULL");
    }

    @Test
    void shouldHaveAuditEventTargetTimeIndex() {
        assertThat(findIndexColumns("idx_audit_event_target_time"))
                .containsExactly("target_type", "target_id", "created_at");
    }

    @Test
    void shouldHaveAuditEventRequestIndex() {
        Map<String, Object> idx = findIndex("idx_audit_event_request");
        assertThat(idx).isNotNull();
    }

    @Test
    void shouldHaveAppendOnlyTriggerFunction() {
        String result = jdbc.queryForObject(
                "SELECT proname FROM pg_proc WHERE proname = 'fn_reject_audit_mutation'",
                String.class
        );
        assertThat(result).isEqualTo("fn_reject_audit_mutation");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void assertColumnType(String table, String column, String expectedType) {
        String actualType = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = ? AND column_name = ?",
                String.class, table, column
        );
        assertThat(actualType).isEqualTo(expectedType);
    }

    private List<String> getColumnNames(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = ? ORDER BY ordinal_position",
                String.class, table
        );
    }

    private List<String> findIndexColumns(String indexName) {
        return jdbc.queryForList(
                "SELECT a.attname FROM pg_index i "
                        + "JOIN pg_class c ON i.indexrelid = c.oid "
                        + "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                        + "WHERE c.relname = ? ORDER BY array_position(i.indkey, a.attnum)",
                String.class, indexName
        );
    }

    private Map<String, Object> findIndex(String indexName) {
        return jdbc.queryForMap(
                "SELECT c.relname, i.indisunique, i.indisprimary "
                        + "FROM pg_index i JOIN pg_class c ON i.indexrelid = c.oid "
                        + "WHERE c.relname = ?",
                indexName
        );
    }
}
