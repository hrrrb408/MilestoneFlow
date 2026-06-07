package com.milestoneflow.identity.domain.type;

/**
 * Stable constants for {@code auth_session.revoke_reason}.
 *
 * <p>These values are stored directly in the database. They must never change
 * once released, because existing rows reference them. New values may be added
 * in future milestones (e.g., {@code LOGOUT}, {@code USER_DISABLED}).
 *
 * <p>The database does not enforce a CHECK constraint on {@code revoke_reason},
 * so these constants prevent typos in application code.
 */
public final class AuthSessionRevokeReason {

    private AuthSessionRevokeReason() {
    }

    /**
     * Session was superseded by a refresh token rotation.
     * A new session in the same family was created with generation + 1.
     */
    public static final String REFRESH_ROTATED = "REFRESH_ROTATED";

    /**
     * An already-rotated refresh token was reused (replay detected).
     * The entire session family is revoked.
     */
    public static final String REFRESH_REPLAY_DETECTED = "REFRESH_REPLAY_DETECTED";

    /**
     * Session expired naturally (refresh token TTL elapsed).
     */
    public static final String REFRESH_EXPIRED = "REFRESH_EXPIRED";

    // ── Future milestones (defined here for reference only) ──────────────

    // public static final String LOGOUT = "LOGOUT";
    // public static final String USER_DISABLED = "USER_DISABLED";
    // public static final String ADMIN_REVOKED = "ADMIN_REVOKED";
}
