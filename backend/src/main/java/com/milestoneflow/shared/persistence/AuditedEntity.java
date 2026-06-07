package com.milestoneflow.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for entities with full auditing fields.
 *
 * <p>Extends {@link TimestampedEntity} with:
 * <ul>
 *   <li>{@code updated_at} — set on every persist/update via JPA Auditing.</li>
 *   <li>{@code created_by} — set on first persist, null when no authenticated user.</li>
 *   <li>{@code updated_by} — set on every persist/update, null when no authenticated user.</li>
 * </ul>
 *
 * <p>Per ADR-BE-005:
 * <ul>
 *   <li>{@code created_by}/{@code updated_by} are nullable (UUID).</li>
 *   <li>No fake system user is created.</li>
 *   <li>When Spring Security is integrated, {@code AuditorAware} will return the
 *       authenticated user's ID instead of {@link java.util.Optional#empty()}.</li>
 * </ul>
 *
 * <p>Only {@code app_user} has these fields in the current schema.
 * {@code auth_session} and {@code verification_token} extend
 * {@link TimestampedEntity} directly since they lack audit columns.
 *
 * @see TimestampedEntity
 * @see JpaAuditingConfiguration
 */
@MappedSuperclass
public abstract class AuditedEntity extends TimestampedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    protected AuditedEntity() {
        // For JPA proxy instantiation only.
    }

    protected AuditedEntity(UUID id) {
        super(id);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
