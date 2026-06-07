package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link VerificationToken}.
 *
 * <p>This interface is internal to the infrastructure layer and must not
 * be injected outside of {@link VerificationTokenRepositoryAdapter}.
 */
interface SpringDataVerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash,
                                                          VerificationTokenPurpose purpose);

    List<VerificationToken> findByUserIdAndPurpose(UUID userId,
                                                   VerificationTokenPurpose purpose);
}
