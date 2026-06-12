package com.milestoneflow.workspace.domain.model;

import com.milestoneflow.shared.persistence.TimestampedEntity;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipRole;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membership relationship between a user and a workspace.
 *
 * <p>Mapped to {@code workspace_membership} table (V003). Each row represents
 * a user's role and status within a specific workspace.
 *
 * <p>Per ADR-BE-006, foreign keys ({@code workspaceId}, {@code userId}) are
 * stored as UUID IDs — no JPA {@code @ManyToOne} relationships to other modules.
 *
 * <p>V0.1 only supports OWNER role. The unique constraint
 * {@code uk_workspace_membership_active_user} ensures one active membership
 * per user in V0.1.
 *
 * @see com.milestoneflow.shared.persistence.TimestampedEntity
 */
@Entity
@Table(name = "workspace_membership")
public class WorkspaceMembership extends TimestampedEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 24)
    private WorkspaceMembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private WorkspaceMembershipStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected WorkspaceMembership() {
    }

    private WorkspaceMembership(UUID id, UUID workspaceId, UUID userId,
                                WorkspaceMembershipRole role,
                                WorkspaceMembershipStatus status,
                                Instant joinedAt) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
    }

    /**
     * Creates a new OWNER membership in ACTIVE status.
     *
     * <p>Used when a user creates a new workspace. The creator automatically
     * becomes the OWNER with ACTIVE status.
     *
     * @param id          client-generated UUID v7
     * @param workspaceId the workspace this membership belongs to
     * @param userId      the user who owns the workspace
     * @param joinedAt    the time the membership was created
     * @return a new transient WorkspaceMembership entity
     */
    public static WorkspaceMembership createOwner(UUID id, UUID workspaceId,
                                                   UUID userId, Instant joinedAt) {
        return new WorkspaceMembership(id, workspaceId, userId,
                WorkspaceMembershipRole.OWNER,
                WorkspaceMembershipStatus.ACTIVE,
                joinedAt);
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceMembershipRole getRole() {
        return role;
    }

    public WorkspaceMembershipStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getVersion() {
        return version;
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "WorkspaceMembership{id=" + getId()
                + ", workspaceId=" + workspaceId
                + ", userId=" + userId
                + ", role=" + role
                + ", status=" + status
                + ", version=" + version
                + '}';
    }
}
