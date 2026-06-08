package com.milestoneflow.identity.domain.model;

import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import com.milestoneflow.shared.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque token session with family-based refresh rotation.
 *
 * <p>Mapped to {@code auth_session} table (V002, V007). Only SHA-256 hashes
 * are stored — never raw tokens.
 *
 * <p>Per ADR-BE-006, the {@code user_id} foreign key is stored as a UUID,
 * not as a JPA {@code @ManyToOne} relationship. No {@code AppUser} object
 * graph is loaded.
 *
 * <p>Note: This entity does <em>not</em> have a {@code version} column.
 * Optimistic locking is not applicable to {@code auth_session}.
 *
 * @see com.milestoneflow.shared.persistence.TimestampedEntity
 */
@Entity
@Table(name = "auth_session")
public class AuthSession extends TimestampedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_token_hash", nullable = false, length = 64, unique = true)
    private String accessTokenHash;

    @Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
    private String refreshTokenHash;

    @Column(name = "session_family_id", nullable = false)
    private UUID sessionFamilyId;

    @Column(name = "refresh_generation", nullable = false)
    private long refreshGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private AuthSessionStatus status;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private Instant refreshExpiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 48)
    private String revokeReason;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected AuthSession() {
    }

    private AuthSession(UUID id, UUID userId, String accessTokenHash,
                        String refreshTokenHash, UUID sessionFamilyId,
                        long refreshGeneration, Instant accessExpiresAt,
                        Instant refreshExpiresAt, String userAgent,
                        String ipAddress) {
        super(id);
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "accessTokenHash must not be null");
        this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash, "refreshTokenHash must not be null");
        this.sessionFamilyId = Objects.requireNonNull(sessionFamilyId, "sessionFamilyId must not be null");
        if (refreshGeneration < 0) {
            throw new IllegalArgumentException("refreshGeneration must not be negative");
        }
        this.refreshGeneration = refreshGeneration;
        this.accessExpiresAt = Objects.requireNonNull(accessExpiresAt, "accessExpiresAt must not be null");
        this.refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt must not be null");
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.status = AuthSessionStatus.ACTIVE;
    }

    /**
     * Creates a new session in {@code ACTIVE} state.
     *
     * <p>The caller is responsible for generating the ID, token hashes,
     * expiration times, and family ID. This method does not generate
     * tokens, compute hashes, or decide token lifetimes.
     *
     * @param id                client-generated UUID v7
     * @param userId            the user this session belongs to
     * @param accessTokenHash   SHA-256 hash of the access token
     * @param refreshTokenHash  SHA-256 hash of the refresh token
     * @param sessionFamilyId   family grouping for rotation
     * @param refreshGeneration initial generation (typically 0)
     * @param accessExpiresAt   when the access token expires
     * @param refreshExpiresAt  when the refresh token expires
     * @param userAgent         optional User-Agent header value
     * @param ipAddress         optional client IP address
     * @return a new transient AuthSession entity
     * @throws IllegalArgumentException if refreshGeneration is negative
     */
    public static AuthSession create(UUID id, UUID userId, String accessTokenHash,
                                     String refreshTokenHash, UUID sessionFamilyId,
                                     long refreshGeneration, Instant accessExpiresAt,
                                     Instant refreshExpiresAt, String userAgent,
                                     String ipAddress) {
        return new AuthSession(id, userId, accessTokenHash, refreshTokenHash,
                sessionFamilyId, refreshGeneration, accessExpiresAt,
                refreshExpiresAt, userAgent, ipAddress);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Records that this session was used.
     *
     * @param usedAt the instant the session was accessed
     */
    public void markUsed(Instant usedAt) {
        this.lastUsedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
    }

    /**
     * Revokes this session. Valid transition: {@code ACTIVE → REVOKED}.
     *
     * <p>If already {@code REVOKED}, this is a no-op (idempotent).
     *
     * @param revokedAt the instant of revocation
     * @param reason    optional reason for revocation
     * @throws IllegalStateException if the session is {@code EXPIRED}
     */
    public void revoke(Instant revokedAt, String reason) {
        if (status == AuthSessionStatus.EXPIRED) {
            throw new IllegalStateException("Cannot revoke an EXPIRED session");
        }
        if (status == AuthSessionStatus.REVOKED) {
            return; // idempotent
        }
        this.status = AuthSessionStatus.REVOKED;
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.revokeReason = reason;
    }

    /**
     * Marks this session as expired. Valid transition: {@code ACTIVE → EXPIRED}.
     *
     * @param now the current instant to compare against expiration times
     * @throws IllegalStateException if the session is not ACTIVE or has not
     *                               actually expired
     */
    public void markExpired(Instant now) {
        if (status != AuthSessionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE sessions can be marked as EXPIRED");
        }
        if (!isRefreshExpired(now)) {
            throw new IllegalStateException("Session has not expired yet");
        }
        this.status = AuthSessionStatus.EXPIRED;
    }

    /**
     * Revokes this session because it was superseded by a refresh rotation.
     *
     * <p>Valid transition: {@code ACTIVE → REVOKED}.
     *
     * @param revokedAt the instant of revocation
     */
    public void revokeAsRotated(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        if (status == AuthSessionStatus.REVOKED) {
            if (AuthSessionRevokeReason.REFRESH_ROTATED.equals(revokeReason)) {
                return; // idempotent — already rotated
            }
            throw new IllegalStateException("Only ACTIVE sessions can be revoked as rotated");
        }
        if (status != AuthSessionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE sessions can be revoked as rotated");
        }
        this.status = AuthSessionStatus.REVOKED;
        this.revokedAt = revokedAt;
        this.revokeReason = AuthSessionRevokeReason.REFRESH_ROTATED;
    }

    /**
     * Revokes this session because of a replay detection event.
     *
     * <p>Can be called on sessions in any status (ACTIVE, REVOKED, or EXPIRED)
     * to ensure the entire family is uniformly marked.
     *
     * @param revokedAt the instant of revocation
     */
    public void revokeAsReplayDetected(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        if (status == AuthSessionStatus.REVOKED
                && AuthSessionRevokeReason.REFRESH_REPLAY_DETECTED.equals(revokeReason)) {
            return; // idempotent
        }
        this.status = AuthSessionStatus.REVOKED;
        this.revokedAt = revokedAt;
        this.revokeReason = AuthSessionRevokeReason.REFRESH_REPLAY_DETECTED;
    }

    /**
     * Returns whether this session was revoked due to refresh token rotation.
     */
    public boolean isRefreshRotated() {
        return status == AuthSessionStatus.REVOKED
                && AuthSessionRevokeReason.REFRESH_ROTATED.equals(revokeReason);
    }

    /**
     * Returns whether the access token has expired.
     *
     * @param now the current instant
     * @return {@code true} if the access token is expired
     */
    public boolean isAccessExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(accessExpiresAt);
    }

    /**
     * Returns whether the refresh token has expired.
     *
     * @param now the current instant
     * @return {@code true} if the refresh token is expired
     */
    public boolean isRefreshExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(refreshExpiresAt);
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getUserId() {
        return userId;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public UUID getSessionFamilyId() {
        return sessionFamilyId;
    }

    public long getRefreshGeneration() {
        return refreshGeneration;
    }

    public AuthSessionStatus getStatus() {
        return status;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    // ── toString (excludes token hashes) ─────────────────────────────────

    @Override
    public String toString() {
        return "AuthSession{id=" + getId()
                + ", userId=" + userId
                + ", sessionFamilyId=" + sessionFamilyId
                + ", refreshGeneration=" + refreshGeneration
                + ", status=" + status
                + ", userAgent='" + userAgent + "'"
                + ", ipAddress='" + ipAddress + "'"
                + ", accessExpiresAt=" + accessExpiresAt
                + ", refreshExpiresAt=" + refreshExpiresAt
                + ", lastUsedAt=" + lastUsedAt
                + ", revokedAt=" + revokedAt
                + ", revokeReason='" + revokeReason + "'"
                + '}';
    }
}
