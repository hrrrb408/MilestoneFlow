package com.milestoneflow.audit.infrastructure.persistence;

import com.milestoneflow.audit.domain.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AuditEvent}.
 *
 * <p>Package-private to prevent direct access from outside the audit infrastructure layer.
 * All access must go through {@link AuditEventRepositoryAdapter}.
 *
 * <p>Only save/find operations are provided — no delete or update.
 * The database trigger enforces append-only at the data level.
 */
@Repository
interface SpringDataAuditEventRepository extends JpaRepository<AuditEvent, java.util.UUID> {
}
