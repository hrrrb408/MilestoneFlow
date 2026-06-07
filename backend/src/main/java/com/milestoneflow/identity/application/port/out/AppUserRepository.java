package com.milestoneflow.identity.application.port.out;

import com.milestoneflow.identity.domain.model.AppUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for {@link AppUser} persistence.
 *
 * <p>This interface belongs to the application layer and must not expose
 * Spring Data types ({@code Page}, {@code Sort}, {@code Specification}, etc.).
 * The concrete implementation lives in the infrastructure layer as an adapter.
 *
 * <p>Per ADR-BE-001 and ADR-BE-006:
 * <ul>
 *   <li>The port does not extend {@code JpaRepository}.</li>
 *   <li>No delete operations — users are disabled, not removed.</li>
 *   <li>Email queries use {@code email_normalized} for case-insensitive lookup.</li>
 * </ul>
 */
public interface AppUserRepository {

    AppUser save(AppUser user);

    Optional<AppUser> findById(UUID id);

    Optional<AppUser> findByEmailNormalized(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);
}
