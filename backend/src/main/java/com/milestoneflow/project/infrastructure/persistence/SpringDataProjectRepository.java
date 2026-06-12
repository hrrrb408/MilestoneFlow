package com.milestoneflow.project.infrastructure.persistence;

import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.project.domain.type.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Project entities.
 *
 * <p>Package-private per ARCH-010. Accessed exclusively through
 * {@link ProjectRepositoryAdapter}.
 */
interface SpringDataProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    List<Project> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, ProjectStatus status);

    List<Project> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
