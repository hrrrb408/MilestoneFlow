package com.milestoneflow.activity.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestoneflow.activity.application.port.out.ActivityLogQueryRepository;
import com.milestoneflow.activity.application.result.ActivityLogRow;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter implementing the activity log query port using NamedParameterJdbcTemplate.
 *
 * <p>Queries the existing {@code audit_event} table directly — no new tables
 * or migrations are needed. The database column {@code action} is aliased as
 * {@code event_type} to match the application-layer naming convention.
 *
 * <p>All queries:
 * <ul>
 *   <li>Filter by {@code workspace_id} for tenant isolation</li>
 *   <li>Use named parameters (never string concatenation)</li>
 *   <li>Order by {@code created_at DESC, id DESC} for deterministic pagination</li>
 *   <li>Apply a configurable {@code LIMIT}</li>
 * </ul>
 *
 * <p>Covered by existing indexes:
 * <ul>
 *   <li>{@code idx_audit_event_workspace_time} — workspace scope queries</li>
 *   <li>{@code idx_audit_event_target_time} — project/milestone/task scope queries</li>
 * </ul>
 */
@Component
public class ActivityLogQueryRepositoryAdapter implements ActivityLogQueryRepository {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ActivityLogQueryRepositoryAdapter(NamedParameterJdbcTemplate jdbc,
                                              ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ── Common SELECT clause ─────────────────────────────────────────────────

    private static final String SELECT_COLUMNS = """
            SELECT id, workspace_id, actor_id, actor_type,
                   action AS event_type, target_type, target_id,
                   summary, metadata, created_at
              FROM audit_event
            """;

    private static final String ORDER_LIMIT = """
             ORDER BY created_at DESC, id DESC
             LIMIT :limit
            """;

    // ── Workspace ────────────────────────────────────────────────────────────

    @Override
    public List<ActivityLogRow> findByWorkspace(UUID workspaceId, int limit,
                                                 String eventType, String targetType) {
        String sql = SELECT_COLUMNS + """
                WHERE workspace_id = :workspaceId
                  AND (:eventType IS NULL OR action = :eventType)
                  AND (:targetType IS NULL OR target_type = :targetType)
                """ + ORDER_LIMIT;

        Map<String, Object> params = new HashMap<>();
        params.put("workspaceId", workspaceId);
        params.put("limit", limit);
        params.put("eventType", eventType);
        params.put("targetType", targetType);

        return jdbc.query(sql, params, this::mapRow);
    }

    // ── Project ──────────────────────────────────────────────────────────────

    @Override
    public List<ActivityLogRow> findByProject(UUID workspaceId, UUID projectId,
                                               int limit) {
        String sql = SELECT_COLUMNS + """
                WHERE workspace_id = :workspaceId
                  AND target_type = 'PROJECT'
                  AND target_id = :projectId
                """ + ORDER_LIMIT;

        return jdbc.query(sql,
                Map.of(
                        "workspaceId", workspaceId,
                        "projectId", projectId,
                        "limit", limit
                ),
                this::mapRow);
    }

    // ── Milestone ────────────────────────────────────────────────────────────

    @Override
    public List<ActivityLogRow> findByMilestone(UUID workspaceId, UUID projectId,
                                                 UUID milestoneId, int limit) {
        String sql = SELECT_COLUMNS + """
                WHERE workspace_id = :workspaceId
                  AND target_type = 'MILESTONE'
                  AND target_id = :milestoneId
                """ + ORDER_LIMIT;

        return jdbc.query(sql,
                Map.of(
                        "workspaceId", workspaceId,
                        "projectId", projectId,
                        "milestoneId", milestoneId,
                        "limit", limit
                ),
                this::mapRow);
    }

    // ── Task ─────────────────────────────────────────────────────────────────

    @Override
    public List<ActivityLogRow> findByTask(UUID workspaceId, UUID projectId,
                                            UUID milestoneId, UUID taskId,
                                            int limit) {
        String sql = SELECT_COLUMNS + """
                WHERE workspace_id = :workspaceId
                  AND target_type = 'TASK'
                  AND target_id = :taskId
                """ + ORDER_LIMIT;

        return jdbc.query(sql,
                Map.of(
                        "workspaceId", workspaceId,
                        "projectId", projectId,
                        "milestoneId", milestoneId,
                        "taskId", taskId,
                        "limit", limit
                ),
                this::mapRow);
    }

    // ── Row mapper ───────────────────────────────────────────────────────────

    private ActivityLogRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String actorIdStr = rs.getString("actor_id");
        String targetTypeStr = rs.getString("target_type");
        String targetIdStr = rs.getString("target_id");
        String workspaceIdStr = rs.getString("workspace_id");

        Timestamp ts = rs.getTimestamp("created_at");
        Instant createdAt = ts != null ? ts.toInstant() : null;

        Map<String, Object> metadata = null;
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                metadata = objectMapper.readValue(metadataJson, METADATA_TYPE);
            } catch (Exception e) {
                // Best-effort: if metadata cannot be parsed, leave as null
                metadata = null;
            }
        }

        return new ActivityLogRow(
                UUID.fromString(rs.getString("id")),
                workspaceIdStr != null ? UUID.fromString(workspaceIdStr) : null,
                actorIdStr != null ? UUID.fromString(actorIdStr) : null,
                rs.getString("actor_type"),
                rs.getString("event_type"),
                targetTypeStr,
                targetIdStr != null ? UUID.fromString(targetIdStr) : null,
                rs.getString("summary"),
                metadata,
                createdAt
        );
    }
}
