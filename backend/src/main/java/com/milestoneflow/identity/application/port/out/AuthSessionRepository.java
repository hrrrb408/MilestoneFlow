package com.milestoneflow.identity.application.port.out;

import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;

import java.time.Instant;
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

    /**
     * Finds an auth session by its primary key with a pessimistic write lock.
     *
     * <p>Used during logout to prevent concurrent refresh operations.
     *
     * @param id the session ID
     * @return the locked session, or empty if not found
     */
    Optional<AuthSession> findByIdForUpdate(UUID id);

    /**
     * Revokes all ACTIVE sessions for a given user in bulk.
     *
     * <p>Sets status to REVOKED, sets revokedAt and revokeReason.
     * Used by logout-all, password change, and password reset.
     *
     * @param userId the user whose sessions to revoke
     * @param now    the revocation timestamp
     * @param reason the revoke reason constant
     */
    void revokeAllByUserId(UUID userId, Instant now, String reason);
}
