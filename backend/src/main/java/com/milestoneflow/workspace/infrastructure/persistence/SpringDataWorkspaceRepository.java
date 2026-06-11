package com.milestoneflow.workspace.infrastructure.persistence;

import com.milestoneflow.workspace.domain.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Workspace} entities.
 *
 * <p>Package-private per ARCH-010: Spring Data Repository interfaces must not be public.
 * Accessed exclusively through {@link WorkspaceRepositoryAdapter}.
 */
interface SpringDataWorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
