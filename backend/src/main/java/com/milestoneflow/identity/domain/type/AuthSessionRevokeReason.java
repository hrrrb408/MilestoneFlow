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

    /**
     * Session was explicitly revoked by a user logout request.
     */
    public static final String LOGOUT = "LOGOUT";

    /**
     * All active sessions for a user were revoked by a logout-all request.
     */
    public static final String LOGOUT_ALL = "LOGOUT_ALL";

    /**
     * All sessions revoked because the user changed their password.
     */
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";

    /**
     * All sessions revoked because the user reset their password.
     */
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    // ── Future milestones (defined here for reference only) ──────────────

    // public static final String USER_DISABLED = "USER_DISABLED";
    // public static final String ADMIN_REVOKED = "ADMIN_REVOKED";
}
