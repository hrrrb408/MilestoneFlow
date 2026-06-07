package com.milestoneflow.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for entities with a creation timestamp.
 *
 * <p>Provides {@code created_at} mapped as {@code timestamptz} in UTC.
 * The value is set automatically by JPA Auditing on first persist.
 *
 * <p>All three Identity entities ({@code AppUser}, {@code AuthSession},
 * {@code VerificationToken}) share this field, so it is factored into
 * a common superclass rather than repeated.
 *
 * @see BaseEntity
 * @see AuditedEntity
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class TimestampedEntity extends BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TimestampedEntity() {
        // For JPA proxy instantiation only.
    }

    protected TimestampedEntity(UUID id) {
        super(id);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
