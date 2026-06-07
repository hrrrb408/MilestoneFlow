package com.milestoneflow.identity.application.port.out;

import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for {@link AuthSession} persistence.
 *
 * <p>This interface belongs to the application layer and must not expose
 * Spring Data types. The concrete implementation lives in the infrastructure
 * layer as an adapter.
 *
 * <p>Query methods align with existing database indexes:
 * <ul>
 *   <li>{@code access_token_hash} — unique index</li>
 *   <li>{@code refresh_token_hash} — unique index</li>
 *   <li>{@code user_id + status} — composite index</li>
 *   <li>{@code session_family_id + status} — composite index</li>
 * </ul>
 *
 * <p>No {@code SELECT FOR UPDATE} methods are provided in this milestone.
 * Pessimistic locking for refresh rotation will be added in MF-BE-009.
 */
public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findById(UUID id);

    Optional<AuthSession> findByAccessTokenHash(String accessTokenHash);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSession> findByUserIdAndStatus(UUID userId, AuthSessionStatus status);

    List<AuthSession> findBySessionFamilyId(UUID sessionFamilyId);
}
