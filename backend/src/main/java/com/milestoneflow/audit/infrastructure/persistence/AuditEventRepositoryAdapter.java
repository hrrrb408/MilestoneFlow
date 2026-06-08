package com.milestoneflow.audit.infrastructure.persistence;

import com.milestoneflow.audit.application.port.out.AuditEventRepository;
import com.milestoneflow.audit.domain.model.AuditEvent;

/**
 * Adapter bridging the application port to Spring Data JPA.
 *
 * <p>Only exposes {@code save}. No update or delete operations
 * are delegated to the Spring Data repository.
 */
@org.springframework.stereotype.Repository
class AuditEventRepositoryAdapter implements AuditEventRepository {

    private final SpringDataAuditEventRepository springDataRepository;

    AuditEventRepositoryAdapter(SpringDataAuditEventRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void save(AuditEvent event) {
        springDataRepository.save(event);
    }
}
