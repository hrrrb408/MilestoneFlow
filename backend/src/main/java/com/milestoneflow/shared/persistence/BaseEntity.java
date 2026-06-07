package com.milestoneflow.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all JPA entities.
 *
 * <p>Provides a single {@code id} column mapped as PostgreSQL {@code uuid}.
 * IDs are generated client-side using UUID v7 via {@link com.milestoneflow.shared.id.IdGenerator},
 * never by the database.
 *
 * <p>Per ADR-BE-002:
 * <ul>
 *   <li>UUID v7 for time-ordered primary keys.</li>
 *   <li>PostgreSQL {@code uuid} column type, never {@code varchar(36)}.</li>
 *   <li>Client-side generation before persistence.</li>
 * </ul>
 *
 * <h3>Equality semantics</h3>
 *
 * <p>Entity equality is based <em>solely</em> on the non-null {@code id} field.
 * Two transient entities (no ID assigned) are never equal to each other.
 * This avoids issues with mutable business fields and Hibernate proxy objects.
 *
 * <p>Cross-table ID equality (e.g., an {@code AppUser} and an {@code AuthSession}
 * with the same UUID) is theoretically possible but practically impossible due to
 * UUID v7's 128-bit space. This trade-off is acceptable per ADR-BE-002.
 *
 * @see TimestampedEntity
 * @see AuditedEntity
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    protected BaseEntity() {
        // For JPA proxy instantiation only.
    }

    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public UUID getId() {
        return id;
    }

    /**
     * Entity equality based on non-null ID only.
     *
     * <p>Uses {@code instanceof} rather than {@code getClass()} to avoid
     * Hibernate proxy issues where the proxy is a subclass of the entity.
     * Two transient entities (null ID) are never equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity other)) {
            return false;
        }
        if (id == null || other.id == null) {
            return false;
        }
        return id.equals(other.id);
    }

    /**
     * Hash code based on non-null ID, falling back to identity hash code
     * for transient entities to satisfy the {@link Object#hashCode()} contract.
     */
    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
