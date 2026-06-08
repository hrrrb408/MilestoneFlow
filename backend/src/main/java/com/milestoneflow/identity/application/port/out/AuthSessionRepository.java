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
 */
public interface AuthSessionRepository {

    AuthSession save(AuthSession session);

    Optional<AuthSession> findById(UUID id);

    Optional<AuthSession> findByAccessTokenHash(String accessTokenHash);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * Finds an auth session by refresh token hash with a pessimistic write lock.
     *
     * <p>Must be called within an active transaction. The lock prevents
     * concurrent refresh operations on the same session.
     *
     * @param refreshTokenHash SHA-256 hash of the raw refresh token
     * @return the locked session, or empty if not found
     */
    Optional<AuthSession> findByRefreshTokenHashForUpdate(String refreshTokenHash);

    List<AuthSession> findByUserIdAndStatus(UUID userId, AuthSessionStatus status);

    List<AuthSession> findBySessionFamilyId(UUID sessionFamilyId);

    /**
     * Finds all ACTIVE sessions in a given family.
     *
     * <p>Used for family-wide revocation during replay detection.
     *
     * @param sessionFamilyId the family to query
     * @return list of ACTIVE sessions in the family
     */
    List<AuthSession> findActiveBySessionFamilyId(UUID sessionFamilyId);
}
