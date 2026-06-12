package com.milestoneflow.workspace.application.port.out;

import com.milestoneflow.workspace.domain.model.Workspace;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for workspace persistence operations.
 *
 * <p>The application layer depends on this port interface; the infrastructure
 * layer provides the adapter implementation. Spring Data types are never
 * exposed through this interface.
 */
public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
