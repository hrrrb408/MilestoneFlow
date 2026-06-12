package com.milestoneflow.milestone.domain.model;

import com.milestoneflow.milestone.domain.type.MilestoneStatus;
import com.milestoneflow.shared.persistence.AuditedEntity;
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
 * Milestone entity — a project-scoped milestone in MilestoneFlow.
 *
 * <p>Mapped to {@code milestone} table (V009). Each milestone belongs to exactly
 * one project within a workspace, identified by {@code workspaceId} and {@code projectId}.
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
@Table(name = "milestone")
public class Milestone extends AuditedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MilestoneStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "settings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> settings;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected Milestone() {
    }

    private Milestone(UUID id, UUID workspaceId, UUID projectId, String title,
                      String description, LocalDate dueDate) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description;
        this.status = MilestoneStatus.OPEN;
        this.dueDate = dueDate;
        this.settings = Map.of();
    }

    /**
     * Creates a new milestone in OPEN status.
     *
     * <p>The caller is responsible for:
     * <ul>
     *   <li>Generating the ID via {@link com.milestoneflow.shared.id.IdGenerator}.</li>
     *   <li>Validating the workspace membership.</li>
     *   <li>Validating that the project exists and is not archived.</li>
     *   <li>Validating that the project belongs to the workspace.</li>
     * </ul>
     *
     * @param id          client-generated UUID v7
     * @param workspaceId the owning workspace
     * @param projectId   the parent project
     * @param title       milestone title
     * @param description milestone description (nullable)
     * @param dueDate     target completion date (nullable)
     * @return a new transient Milestone entity
     */
    public static Milestone create(UUID id, UUID workspaceId, UUID projectId,
                                   String title, String description, LocalDate dueDate) {
        return new Milestone(id, workspaceId, projectId, title, description, dueDate);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Completes this milestone.
     *
     * <p>Transitions status from OPEN to COMPLETED. Sets {@code completedAt}
     * and {@code completedBy}. Throws if already COMPLETED.
     *
     * @param actorId     the user performing the completion
     * @param completedAt the completion timestamp
     * @throws IllegalStateException if milestone is already COMPLETED
     */
    public void complete(UUID actorId, Instant completedAt) {
        if (this.status == MilestoneStatus.COMPLETED) {
            throw new IllegalStateException("Milestone is already completed");
        }
        this.status = MilestoneStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.completedBy = Objects.requireNonNull(actorId, "actorId must not be null");
    }

    /**
     * Reopens this milestone.
     *
     * <p>Transitions status from COMPLETED back to OPEN. Clears
     * {@code completedAt} and {@code completedBy}. Throws if not COMPLETED.
     *
     * @throws IllegalStateException if milestone is not COMPLETED
     */
    public void reopen() {
        if (this.status != MilestoneStatus.COMPLETED) {
            throw new IllegalStateException("Milestone is not completed");
        }
        this.status = MilestoneStatus.OPEN;
        this.completedAt = null;
        this.completedBy = null;
    }

    /**
     * Returns whether this milestone is in COMPLETED status.
     *
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return this.status == MilestoneStatus.COMPLETED;
    }

    /**
     * Updates basic milestone information.
     *
     * <p>Only non-null parameters are applied; null parameters are ignored.
     * COMPLETED milestones cannot be updated — reopen first.
     *
     * @param title       new title (nullable to skip)
     * @param description new description (nullable to skip)
     * @param dueDate     new due date (nullable to skip)
     * @throws IllegalStateException if milestone is COMPLETED
     */
    public void updateBasicInfo(String title, String description, LocalDate dueDate) {
        if (this.status == MilestoneStatus.COMPLETED) {
            throw new IllegalStateException("Cannot update a completed milestone");
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public long getVersion() {
        return version;
    }

    // ── toString (excludes settings) ─────────────────────────────────────

    @Override
    public String toString() {
        return "Milestone{id=" + getId()
                + ", workspaceId=" + workspaceId
                + ", projectId=" + projectId
                + ", title='" + title + "'"
                + ", status=" + status
                + ", dueDate=" + dueDate
                + ", version=" + version
                + '}';
    }
}
