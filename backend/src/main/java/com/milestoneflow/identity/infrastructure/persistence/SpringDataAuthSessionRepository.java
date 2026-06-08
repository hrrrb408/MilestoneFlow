package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuthSession}.
 *
 * <p>This interface is internal to the infrastructure layer and must not
 * be injected outside of {@link AuthSessionRepositoryAdapter}.
 */
interface SpringDataAuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByAccessTokenHash(String accessTokenHash);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findLockedByRefreshTokenHash(String refreshTokenHash);

    List<AuthSession> findByUserIdAndStatus(UUID userId, AuthSessionStatus status);

    List<AuthSession> findBySessionFamilyId(UUID sessionFamilyId);

    List<AuthSession> findBySessionFamilyIdAndStatus(UUID sessionFamilyId, AuthSessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findLockedById(UUID id);

    @Modifying
    @Query("UPDATE AuthSession s SET s.status = 'REVOKED', s.revokedAt = :now, s.revokeReason = :reason "
            + "WHERE s.userId = :userId AND s.status = 'ACTIVE'")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now, @Param("reason") String reason);
}
