package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AppUser}.
 *
 * <p>This interface is internal to the infrastructure layer and must not
 * be injected outside of {@link AppUserRepositoryAdapter}. Application
 * services depend on the port interface, not this Spring Data interface.
 */
interface SpringDataAppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailNormalized(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);
}
