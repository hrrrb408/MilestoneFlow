package com.milestoneflow.task.domain.model;

import com.milestoneflow.shared.persistence.AuditedEntity;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;
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
 * Task entity — a milestone-scoped task in MilestoneFlow.
 *
 * <p>Mapped to {@code task} table (V010). Each task belongs to exactly one
 * milestone within a project within a workspace, identified by
 * {@code workspaceId}, {@code projectId}, and {@code milestoneId}.
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
@Table(name = "task")
public class Task extends AuditedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 32)
    private TaskPriority priority;

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
    protected Task() {
    }

    private Task(UUID id, UUID workspaceId, UUID projectId, UUID milestoneId,
                 String title, String description, TaskPriority priority,
                 LocalDate dueDate) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.milestoneId = Objects.requireNonNull(milestoneId, "milestoneId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 160) {
            throw new IllegalArgumentException("title must be at most 160 characters");
        }
        this.description = description;
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        this.dueDate = dueDate;
        this.status = TaskStatus.OPEN;
        this.completedAt = null;
        this.completedBy = null;
        this.settings = Map.of();
    }

    /**
     * Creates a new task in OPEN status.
     *
     * <p>The caller is responsible for:
     * <ul>
     *   <li>Generating the ID via {@link com.milestoneflow.shared.id.IdGenerator}.</li>
     *   <li>Validating the workspace membership.</li>
     *   <li>Validating that the project exists and is not archived.</li>
     *   <li>Validating that the milestone exists and is not completed.</li>
     *   <li>Validating that the project belongs to the workspace.</li>
     *   <li>Validating that the milestone belongs to the project.</li>
     * </ul>
     *
     * @param id          client-generated UUID v7
     * @param workspaceId the owning workspace
     * @param projectId   the parent project
     * @param milestoneId the parent milestone
     * @param title       task title
     * @param description task description (nullable)
     * @param priority    task priority (defaults to MEDIUM if null from caller —
     *                    caller should supply {@link TaskPriority#MEDIUM})
     * @param dueDate     target completion date (nullable)
     * @return a new transient Task entity
     */
    public static Task create(UUID id, UUID workspaceId, UUID projectId,
                              UUID milestoneId, String title, String description,
                              TaskPriority priority, LocalDate dueDate) {
        return new Task(id, workspaceId, projectId, milestoneId,
                title, description, priority, dueDate);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Returns whether this task is in COMPLETED status.
     *
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return this.status == TaskStatus.COMPLETED;
    }

    /**
     * Completes this task.
     *
     * <p>Transitions status from OPEN to COMPLETED. Sets {@code completedAt}
     * and {@code completedBy}. Throws if already COMPLETED.
     *
     * @param actorId     the user performing the completion
     * @param completedAt the completion timestamp
     * @throws IllegalStateException if task is already COMPLETED
     */
    public void complete(UUID actorId, Instant completedAt) {
        if (this.status == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Task is already completed");
        }
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.completedBy = Objects.requireNonNull(actorId, "actorId must not be null");
    }

    /**
     * Reopens this task.
     *
     * <p>Transitions status from COMPLETED back to OPEN. Clears
     * {@code completedAt} and {@code completedBy}. Throws if not COMPLETED.
     *
     * @throws IllegalStateException if task is not COMPLETED
     */
    public void reopen() {
        if (this.status != TaskStatus.COMPLETED) {
            throw new IllegalStateException("Task is not completed");
        }
        this.status = TaskStatus.OPEN;
        this.completedAt = null;
        this.completedBy = null;
    }

    /**
     * Updates basic task information.
     *
     * <p>Only non-null parameters are applied; null parameters are ignored.
     * COMPLETED tasks cannot be updated — reopen first.
     *
     * @param title       new title (nullable to skip)
     * @param description new description (nullable to skip)
     * @param priority    new priority (nullable to skip)
     * @param dueDate     new due date (nullable to skip)
     * @throws IllegalStateException if task is COMPLETED
     */
    public void updateBasicInfo(String title, String description,
                                TaskPriority priority, LocalDate dueDate) {
        if (this.status == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Cannot update a completed task");
        }
        if (title != null) {
            if (title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            if (title.length() > 160) {
                throw new IllegalArgumentException("title must be at most 160 characters");
            }
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (priority != null) {
            this.priority = priority;
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

    public UUID getMilestoneId() {
        return milestoneId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
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
        return "Task{id=" + getId()
                + ", workspaceId=" + workspaceId
                + ", projectId=" + projectId
                + ", milestoneId=" + milestoneId
                + ", title='" + title + "'"
                + ", status=" + status
                + ", priority=" + priority
                + ", dueDate=" + dueDate
                + ", version=" + version
                + '}';
    }
}
