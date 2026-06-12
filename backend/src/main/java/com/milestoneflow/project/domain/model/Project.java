package com.milestoneflow.project.domain.model;

import com.milestoneflow.shared.persistence.AuditedEntity;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotArchivedException;
import com.milestoneflow.project.domain.type.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Project entity — a workspace-scoped project in MilestoneFlow.
 *
 * <p>Mapped to {@code project} table (V008). Each project belongs to exactly
 * one workspace, identified by {@code workspaceId}.
 *
 * <p>Per ADR-BE-001, the domain model directly serves as the JPA entity.
 * Per ADR-BE-006, foreign keys are stored as UUID IDs — no JPA {@code @ManyToOne}
 * relationships to other modules.
 *
 * <h3>Sensitive data</h3>
 * <p>{@code settings} JSONB is excluded from {@link #toString()} to prevent
 * accidental logging of potentially sensitive configuration data.
 *
 * @see com.milestoneflow.shared.persistence.AuditedEntity
 */
@Entity
@Table(name = "project")
public class Project extends AuditedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "settings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> settings;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected Project() {
    }

    private Project(UUID id, UUID workspaceId, String name, String description,
                    LocalDate startDate, LocalDate targetDate) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.status = ProjectStatus.ACTIVE;
        this.startDate = startDate;
        this.targetDate = targetDate;
        this.settings = Map.of();
    }

    /**
     * Creates a new project in ACTIVE state.
     *
     * <p>The caller is responsible for:
     * <ul>
     *   <li>Generating the ID via {@link com.milestoneflow.shared.id.IdGenerator}.</li>
     *   <li>Validating the workspace membership.</li>
     *   <li>Validating the date range (startDate &lt;= targetDate).</li>
     * </ul>
     *
     * @param id          client-generated UUID v7
     * @param workspaceId the owning workspace
     * @param name        project display name
     * @param description project description (nullable)
     * @param startDate   planned start date (nullable)
     * @param targetDate  target completion date (nullable)
     * @return a new transient Project entity
     */
    public static Project create(UUID id, UUID workspaceId, String name,
                                 String description, LocalDate startDate,
                                 LocalDate targetDate) {
        return new Project(id, workspaceId, name, description, startDate, targetDate);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Updates basic project information.
     *
     * <p>Only non-null parameters are applied; null parameters are ignored.
     * Must not be called on archived projects — callers must check first.
     *
     * @param name        new name (nullable to skip)
     * @param description new description (nullable to skip)
     * @param startDate   new start date (nullable to skip)
     * @param targetDate  new target date (nullable to skip)
     * @throws ProjectArchivedException if the project is ARCHIVED
     */
    public void updateBasicInfo(String name, String description,
                                LocalDate startDate, LocalDate targetDate) {
        if (this.status == ProjectStatus.ARCHIVED) {
            throw new ProjectArchivedException();
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (targetDate != null) {
            this.targetDate = targetDate;
        }
    }

    /**
     * Archives this project.
     *
     * <p>Transitions from ACTIVE to ARCHIVED, recording who archived it and when.
     *
     * @param actorId    the user performing the archive
     * @param archivedAt the timestamp of the archive
     * @throws ProjectArchivedException if the project is already ARCHIVED
     */
    public void archive(UUID actorId, Instant archivedAt) {
        if (this.status == ProjectStatus.ARCHIVED) {
            throw new ProjectArchivedException();
        }
        this.status = ProjectStatus.ARCHIVED;
        this.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt must not be null");
        this.archivedBy = Objects.requireNonNull(actorId, "actorId must not be null");
    }

    /**
     * Restores this project from ARCHIVED to ACTIVE.
     *
     * <p>Clears archivedAt and archivedBy.
     *
     * @throws ProjectNotArchivedException if the project is not ARCHIVED
     */
    public void restore() {
        if (this.status != ProjectStatus.ARCHIVED) {
            throw new ProjectNotArchivedException();
        }
        this.status = ProjectStatus.ACTIVE;
        this.archivedAt = null;
        this.archivedBy = null;
    }

    /**
     * Returns whether this project is archived.
     */
    public boolean isArchived() {
        return this.status == ProjectStatus.ARCHIVED;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public UUID getArchivedBy() {
        return archivedBy;
    }

    public long getVersion() {
        return version;
    }

    // ── toString (excludes settings) ─────────────────────────────────────

    @Override
    public String toString() {
        return "Project{id=" + getId()
                + ", workspaceId=" + workspaceId
                + ", name='" + name + "'"
                + ", status=" + status
                + ", startDate=" + startDate
                + ", targetDate=" + targetDate
                + ", version=" + version
                + '}';
    }
}
