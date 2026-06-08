package com.milestoneflow.audit.domain.model;

import com.milestoneflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable audit event entity mapped to the {@code audit_event} table (V004).
 *
 * <p>Append-only: UPDATE and DELETE are rejected by database triggers.
 * This entity provides no mutation methods beyond construction.
 *
 * <p>Per B1 Baseline §16.6, metadata must never contain sensitive fields:
 * password, passwordHash, rawToken, tokenHash, cookie, authorization,
 * resetToken, verificationToken, refreshToken, accessToken.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent extends BaseEntity {

    /**
     * Sensitive metadata keys that must never appear in audit metadata.
     */
    static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "password", "passwordhash", "rawtoken", "tokenhash",
            "cookie", "authorization", "resettoken", "verificationtoken",
            "refreshtoken", "accesstoken"
    );

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 24)
    private String actorType;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 48)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "source", nullable = false, length = 24)
    private String source;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected AuditEvent() {
    }

    private AuditEvent(UUID id, UUID actorId, String actorType, String action,
                       String targetType, UUID targetId, UUID workspaceId,
                       String requestId, String source, String summary,
                       Map<String, Object> metadata, Instant createdAt) {
        super(id);
        this.actorId = actorId;
        this.actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.targetType = targetType;
        this.targetId = targetId;
        this.workspaceId = workspaceId;
        this.requestId = requestId;
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.summary = Objects.requireNonNull(summary, "summary must not be null");
        this.metadata = metadata != null ? sanitizeMetadata(metadata) : null;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Creates a new immutable audit event.
     *
     * <p>Metadata is sanitized to remove any sensitive keys.
     *
     * @param id          client-generated UUID v7
     * @param actorId     the user who performed the action (nullable for SYSTEM/JOB)
     * @param actorType   USER, SYSTEM, or JOB
     * @param action      the event action (e.g., AUTH_LOGIN_SUCCEEDED)
     * @param targetType  the type of target entity (nullable)
     * @param targetId    the ID of the target entity (nullable)
     * @param workspaceId workspace context (nullable for identity events)
     * @param requestId   request correlation ID (nullable)
     * @param source      API, INTERNAL, JOB, or CRON
     * @param summary     human-readable summary
     * @param metadata    additional context (sanitized for sensitive keys)
     * @param createdAt   event timestamp
     * @return a new transient AuditEvent
     * @throws IllegalArgumentException if metadata contains forbidden keys
     */
    public static AuditEvent create(UUID id, UUID actorId, String actorType,
                                    String action, String targetType, UUID targetId,
                                    UUID workspaceId, String requestId, String source,
                                    String summary, Map<String, Object> metadata,
                                    Instant createdAt) {
        return new AuditEvent(id, actorId, actorType, action, targetType, targetId,
                workspaceId, requestId, source, summary, metadata, createdAt);
    }

    /**
     * Sanitizes metadata by checking for forbidden sensitive keys.
     *
     * @param metadata the raw metadata map
     * @return the sanitized metadata map
     * @throws IllegalArgumentException if a forbidden key is found
     */
    static Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        for (String key : metadata.keySet()) {
            if (FORBIDDEN_METADATA_KEYS.contains(key.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Forbidden metadata key: '" + key + "'. "
                                + "Sensitive data must not appear in audit events.");
            }
        }
        return Map.copyOf(metadata);
    }

    // ── Getters (no setters — immutable) ──────────────────────────────────

    public UUID getActorId() {
        return actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSource() {
        return source;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "AuditEvent{id=" + getId()
                + ", actorType='" + actorType + "'"
                + ", action='" + action + "'"
                + ", source='" + source + "'"
                + ", requestId='" + requestId + "'"
                + ", createdAt=" + createdAt
                + '}';
    }
}
