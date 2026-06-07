package com.milestoneflow.identity.domain.model;

import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
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
 * Single-use security token for email verification and password reset.
 *
 * <p>Mapped to {@code verification_token} table (V002). Only SHA-256 hashes
 * are stored — never raw tokens.
 *
 * <p>Per ADR-BE-006, the {@code user_id} foreign key is stored as a UUID,
 * not as a JPA {@code @ManyToOne} relationship.
 *
 * <p>Note: This entity does <em>not</em> have a {@code version} or
 * {@code revoked_at} column. Token revocation is handled by the application
 * service layer (e.g., invalidating all tokens for a user) rather than
 * per-token revocation.
 *
 * @see com.milestoneflow.shared.persistence.TimestampedEntity
 */
@Entity
@Table(name = "verification_token")
public class VerificationToken extends TimestampedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 48)
    private VerificationTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected VerificationToken() {
    }

    private VerificationToken(UUID id, UUID userId, VerificationTokenPurpose purpose,
                              String tokenHash, Instant expiresAt) {
        super(id);
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    /**
     * Creates a new verification token.
     *
     * <p>The caller is responsible for generating the ID and computing
     * the token hash. This method does not generate raw tokens or
     * compute hashes.
     *
     * @param id        client-generated UUID v7
     * @param userId    the user this token belongs to
     * @param purpose   what the token is used for
     * @param tokenHash SHA-256 hash of the raw token
     * @param expiresAt when the token expires
     * @return a new transient VerificationToken entity
     */
    public static VerificationToken create(UUID id, UUID userId,
                                           VerificationTokenPurpose purpose,
                                           String tokenHash, Instant expiresAt) {
        return new VerificationToken(id, userId, purpose, tokenHash, expiresAt);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Marks this token as used.
     *
     * @param usedAt the instant the token was consumed
     * @throws IllegalStateException if the token has already been used
     */
    public void markUsed(Instant usedAt) {
        if (this.usedAt != null) {
            throw new IllegalStateException("Token has already been used");
        }
        this.usedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
    }

    /**
     * Returns whether this token has expired.
     *
     * @param now the current instant
     * @return {@code true} if the token has expired
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    /**
     * Returns whether this token is usable: not used, not expired.
     *
     * @param now the current instant
     * @return {@code true} if the token can still be used
     */
    public boolean isUsable(Instant now) {
        return usedAt == null && !isExpired(now);
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getUserId() {
        return userId;
    }

    public VerificationTokenPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    // ── toString (excludes token hash) ───────────────────────────────────

    @Override
    public String toString() {
        return "VerificationToken{id=" + getId()
                + ", userId=" + userId
                + ", purpose=" + purpose
                + ", expiresAt=" + expiresAt
                + ", usedAt=" + usedAt
                + '}';
    }
}
