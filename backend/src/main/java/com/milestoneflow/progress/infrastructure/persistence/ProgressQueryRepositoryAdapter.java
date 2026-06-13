package com.milestoneflow.progress.infrastructure.persistence;

import com.milestoneflow.progress.application.port.out.ProgressQueryRepository;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.MilestoneCountProjection;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.MilestoneTaskCountProjection;
import com.milestoneflow.progress.application.port.out.ProgressQueryRepository.TaskCountProjection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter that implements the progress query port using NamedParameterJdbcTemplate.
 *
 * <p>Uses native SQL with PostgreSQL conditional aggregates
 * ({@code COUNT(*) FILTER (WHERE ...)}) for single-scan counting.
 * All queries include workspace_id (and project_id) for tenant isolation.
 *
 * <p>No JPA entities are loaded — this is a pure read-model adapter.
 */
@Component
public class ProgressQueryRepositoryAdapter implements ProgressQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProgressQueryRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TaskCountProjection countTasksByProject(UUID workspaceId, UUID projectId) {
        String sql = """
                SELECT COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks
                  FROM task t
                 WHERE t.workspace_id = :workspaceId
                   AND t.project_id = :projectId
                """;
        return jdbc.queryForObject(sql,
                Map.of("workspaceId", workspaceId, "projectId", projectId),
                (rs, rowNum) -> new TaskCountProjection(
                        rs.getLong("total_tasks"),
                        rs.getLong("completed_tasks")));
    }

    @Override
    public TaskCountProjection countTasksByMilestone(UUID workspaceId, UUID projectId,
                                                      UUID milestoneId) {
        String sql = """
                SELECT COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks
                  FROM task t
                 WHERE t.workspace_id = :workspaceId
                   AND t.project_id = :projectId
                   AND t.milestone_id = :milestoneId
                """;
        return jdbc.queryForObject(sql,
                Map.of("workspaceId", workspaceId,
                       "projectId", projectId,
                       "milestoneId", milestoneId),
                (rs, rowNum) -> new TaskCountProjection(
                        rs.getLong("total_tasks"),
                        rs.getLong("completed_tasks")));
    }

    @Override
    public MilestoneCountProjection countMilestonesByProject(UUID workspaceId, UUID projectId) {
        String sql = """
                SELECT COUNT(*) AS total_milestones,
                       COUNT(*) FILTER (WHERE m.status = 'COMPLETED') AS completed_milestones,
                       COUNT(*) FILTER (WHERE m.status = 'OPEN') AS open_milestones
                  FROM milestone m
                 WHERE m.workspace_id = :workspaceId
                   AND m.project_id = :projectId
                """;
        return jdbc.queryForObject(sql,
                Map.of("workspaceId", workspaceId, "projectId", projectId),
                (rs, rowNum) -> new MilestoneCountProjection(
                        rs.getLong("total_milestones"),
                        rs.getLong("completed_milestones"),
                        rs.getLong("open_milestones")));
    }

    @Override
    public List<MilestoneTaskCountProjection> countTasksPerMilestone(UUID workspaceId,
                                                                      UUID projectId) {
        String sql = """
                SELECT m.id            AS milestone_id,
                       m.title         AS title,
                       m.status        AS milestone_status,
                       COUNT(t.id)     AS total_tasks,
                       COUNT(t.id) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks
                  FROM milestone m
                  LEFT JOIN task t
                         ON t.milestone_id = m.id
                        AND t.workspace_id = m.workspace_id
                        AND t.project_id = m.project_id
                 WHERE m.workspace_id = :workspaceId
                   AND m.project_id = :projectId
                 GROUP BY m.id, m.title, m.status, m.due_date, m.created_at
                 ORDER BY
                       CASE WHEN m.due_date IS NULL THEN 1 ELSE 0 END,
                       m.due_date ASC,
                       m.created_at ASC
                """;
        return jdbc.query(sql,
                Map.of("workspaceId", workspaceId, "projectId", projectId),
                (rs, rowNum) -> new MilestoneTaskCountProjection(
                        UUID.fromString(rs.getString("milestone_id")),
                        rs.getString("title"),
                        rs.getLong("total_tasks"),
                        rs.getLong("completed_tasks"),
                        rs.getString("milestone_status")));
    }
}
