package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.tokenHash = :tokenHash AND vt.purpose = :purpose")
    Optional<VerificationToken> findByTokenHashAndPurposeForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") VerificationTokenPurpose purpose);

    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.userId = :userId "
            + "AND vt.purpose = :purpose AND vt.usedAt IS NULL")
    int deleteUnusedByUserIdAndPurpose(
            @Param("userId") UUID userId,
            @Param("purpose") VerificationTokenPurpose purpose);
}
