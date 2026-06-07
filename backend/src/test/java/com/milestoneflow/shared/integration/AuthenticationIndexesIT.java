package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for V002–V005 index structure verification.
 * Validates index definitions, uniqueness, column order, and partial index conditions.
 */
class AuthenticationIndexesIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns index details from pg_indexes for a given table and index name.
     */
    private Map<String, Object> getIndexDefinition(String tableName, String indexName) {
        return jdbc.queryForMap(
                "SELECT indexname, indexdef FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
                tableName, indexName
        );
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
                Integer.class, tableName, indexName
        );
        return count != null && count > 0;
    }

    /**
     * Returns the list of column names for a given index using pg_get_indexdef.
     */
    private List<String> getIndexColumns(String indexName) {
        return jdbc.queryForList(
                "SELECT a.attname FROM pg_index i "
                        + "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ? "
                        + "ORDER BY array_position(i.indkey, a.attnum)",
                String.class, indexName
        );
    }

    private boolean isUniqueIndex(String indexName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ? AND i.indisunique",
                Integer.class, indexName
        );
        return count != null && count > 0;
    }

    // ── app_user indexes ──────────────────────────────────────────────────

    @Nested
    class AppUserIndexes {

        @Test
        void emailNormalizedShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_app_user_email_normalized")).isTrue();
        }

        @Test
        void emailNormalizedIndexShouldBeOnCorrectColumn() {
            List<String> cols = getIndexColumns("uk_app_user_email_normalized");
            assertThat(cols).containsExactly("email_normalized");
        }
    }

    // ── auth_session indexes ──────────────────────────────────────────────

    @Nested
    class AuthSessionIndexes {

        @Test
        void accessTokenHashShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_auth_session_access_hash")).isTrue();
        }

        @Test
        void accessTokenHashIndexShouldBeOnCorrectColumn() {
            List<String> cols = getIndexColumns("uk_auth_session_access_hash");
            assertThat(cols).containsExactly("access_token_hash");
        }

        @Test
        void refreshTokenHashShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_auth_session_refresh_hash")).isTrue();
        }

        @Test
        void refreshTokenHashIndexShouldBeOnCorrectColumn() {
            List<String> cols = getIndexColumns("uk_auth_session_refresh_hash");
            assertThat(cols).containsExactly("refresh_token_hash");
        }

        @Test
        void sessionFamilyGenerationShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_auth_session_family_gen")).isTrue();
        }

        @Test
        void sessionFamilyGenerationIndexColumns() {
            List<String> cols = getIndexColumns("uk_auth_session_family_gen");
            assertThat(cols).containsExactly("session_family_id", "refresh_generation");
        }

        @Test
        void userStatusIndexShouldExist() {
            assertThat(indexExists("auth_session", "idx_auth_session_user_status")).isTrue();
        }

        @Test
        void userStatusIndexColumns() {
            List<String> cols = getIndexColumns("idx_auth_session_user_status");
            assertThat(cols).containsExactly("user_id", "status");
        }

        @Test
        void familyStatusIndexShouldExist() {
            assertThat(indexExists("auth_session", "idx_auth_session_family_status")).isTrue();
        }

        @Test
        void familyGenerationRedundantIndexShouldNotExist() {
            // V006 removed idx_auth_session_family because it duplicated the
            // unique constraint index uk_auth_session_family_gen on the same columns.
            assertThat(indexExists("auth_session", "idx_auth_session_family")).isFalse();
        }

        @Test
        void familyGenerationUniqueConstraintIndexShouldExist() {
            // The unique constraint uk_auth_session_family_gen creates an implicit
            // unique B-tree index — this is the authoritative index for family generation lookup.
            assertThat(indexExists("auth_session", "uk_auth_session_family_gen")).isTrue();
            assertThat(isUniqueIndex("uk_auth_session_family_gen")).isTrue();
            List<String> cols = getIndexColumns("uk_auth_session_family_gen");
            assertThat(cols).containsExactly("session_family_id", "refresh_generation");
        }
    }

    // ── verification_token indexes ────────────────────────────────────────

    @Nested
    class VerificationTokenIndexes {

        @Test
        void tokenHashShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_verification_token_hash")).isTrue();
        }

        @Test
        void tokenHashIndexColumns() {
            List<String> cols = getIndexColumns("uk_verification_token_hash");
            assertThat(cols).containsExactly("token_hash");
        }

        @Test
        void userPurposeIndexShouldExist() {
            assertThat(indexExists("verification_token", "idx_verification_token_user_purpose")).isTrue();
        }

        @Test
        void userPurposeIndexColumns() {
            List<String> cols = getIndexColumns("idx_verification_token_user_purpose");
            assertThat(cols).containsExactly("user_id", "purpose");
        }

        @Test
        void tokenLookupPartialIndexShouldExist() {
            assertThat(indexExists("verification_token", "idx_verification_token_lookup")).isTrue();
        }

        @Test
        void tokenLookupPartialIndexShouldHaveWhereClause() {
            Map<String, Object> idx = getIndexDefinition("verification_token", "idx_verification_token_lookup");
            String def = (String) idx.get("indexdef");
            assertThat(def).containsIgnoringCase("WHERE").contains("used_at IS NULL");
        }
    }

    // ── workspace indexes ─────────────────────────────────────────────────

    @Nested
    class WorkspaceIndexes {

        @Test
        void slugShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_workspace_slug")).isTrue();
        }
    }

    // ── workspace_membership indexes ──────────────────────────────────────

    @Nested
    class MembershipIndexes {

        @Test
        void workspaceUserShouldHaveUniqueIndex() {
            assertThat(isUniqueIndex("uk_workspace_membership")).isTrue();
        }

        @Test
        void workspaceUserIndexColumns() {
            List<String> cols = getIndexColumns("uk_workspace_membership");
            assertThat(cols).containsExactly("workspace_id", "user_id");
        }

        @Test
        void activeOwnerPartialUniqueIndexShouldExist() {
            assertThat(indexExists("workspace_membership", "uk_workspace_membership_active_owner")).isTrue();
            assertThat(isUniqueIndex("uk_workspace_membership_active_owner")).isTrue();
        }

        @Test
        void activeOwnerPartialIndexShouldFilterCorrectly() {
            Map<String, Object> idx = getIndexDefinition("workspace_membership", "uk_workspace_membership_active_owner");
            String def = (String) idx.get("indexdef");
            assertThat(def).containsIgnoringCase("WHERE")
                    .contains("role").contains("OWNER")
                    .contains("status").contains("ACTIVE");
        }

        @Test
        void activeOwnerPartialIndexColumns() {
            List<String> cols = getIndexColumns("uk_workspace_membership_active_owner");
            assertThat(cols).containsExactly("workspace_id");
        }

        @Test
        void activeUserPartialUniqueIndexShouldExist() {
            assertThat(indexExists("workspace_membership", "uk_workspace_membership_active_user")).isTrue();
            assertThat(isUniqueIndex("uk_workspace_membership_active_user")).isTrue();
        }

        @Test
        void activeUserPartialIndexShouldFilterCorrectly() {
            Map<String, Object> idx = getIndexDefinition("workspace_membership", "uk_workspace_membership_active_user");
            String def = (String) idx.get("indexdef");
            assertThat(def).containsIgnoringCase("WHERE").contains("status").contains("ACTIVE");
        }

        @Test
        void activeUserPartialIndexColumns() {
            List<String> cols = getIndexColumns("uk_workspace_membership_active_user");
            assertThat(cols).containsExactly("user_id");
        }

        @Test
        void workspaceStatusIndexShouldExist() {
            assertThat(indexExists("workspace_membership", "idx_workspace_membership_workspace_status")).isTrue();
        }

        @Test
        void userStatusIndexShouldExist() {
            assertThat(indexExists("workspace_membership", "idx_workspace_membership_user_status")).isTrue();
        }
    }

    // ── audit_event indexes ───────────────────────────────────────────────

    @Nested
    class AuditEventIndexes {

        @Test
        void actorTimeIndexShouldExist() {
            assertThat(indexExists("audit_event", "idx_audit_event_actor_time")).isTrue();
        }

        @Test
        void actorTimeIndexColumns() {
            List<String> cols = getIndexColumns("idx_audit_event_actor_time");
            assertThat(cols).containsExactly("actor_id", "created_at");
        }

        @Test
        void actorTimeIndexShouldBePartial() {
            Map<String, Object> idx = getIndexDefinition("audit_event", "idx_audit_event_actor_time");
            String def = (String) idx.get("indexdef");
            assertThat(def).containsIgnoringCase("WHERE").contains("actor_id IS NOT NULL");
        }

        @Test
        void targetTimeIndexShouldExist() {
            assertThat(indexExists("audit_event", "idx_audit_event_target_time")).isTrue();
        }

        @Test
        void targetTimeIndexColumns() {
            List<String> cols = getIndexColumns("idx_audit_event_target_time");
            assertThat(cols).containsExactly("target_type", "target_id", "created_at");
        }

        @Test
        void workspaceTimeIndexShouldExist() {
            assertThat(indexExists("audit_event", "idx_audit_event_workspace_time")).isTrue();
        }

        @Test
        void workspaceTimeIndexShouldBePartial() {
            Map<String, Object> idx = getIndexDefinition("audit_event", "idx_audit_event_workspace_time");
            String def = (String) idx.get("indexdef");
            assertThat(def).containsIgnoringCase("WHERE").contains("workspace_id IS NOT NULL");
        }

        @Test
        void requestIndexShouldExist() {
            assertThat(indexExists("audit_event", "idx_audit_event_request")).isTrue();
        }

        @Test
        void requestIndexShouldBePartial() {
            Map<String, Object> idx = getIndexDefinition("audit_event", "idx_audit_event_request");
            String def = (String) idx.get("indexdef");
            assertThat(def).containsIgnoringCase("WHERE").contains("request_id IS NOT NULL");
        }
    }

    // ── No Redundant Indexes ──────────────────────────────────────────────

    @Nested
    class NoRedundantIndexes {

        @Test
        void shouldNotHaveRedundantAppUserStatusIndex() {
            // email_normalized UNIQUE already covers the login query;
            // a separate status index is low-cardinality and low-value.
            assertThat(indexExists("app_user", "idx_app_user_status")).isFalse();
        }

        @Test
        void shouldNotHaveJsonbGinIndexes() {
            // No GIN indexes on jsonb columns until a real query need exists.
            Integer ginCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes i "
                            + "JOIN pg_class c ON c.relname = i.indexname "
                            + "JOIN pg_am a ON a.oid = c.relam "
                            + "WHERE i.schemaname = 'public' AND a.amname = 'gin'",
                    Integer.class
            );
            assertThat(ginCount).isZero();
        }
    }
}
