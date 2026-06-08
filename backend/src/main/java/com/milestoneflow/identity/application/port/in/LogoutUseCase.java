package com.milestoneflow.identity.application.port.in;

import java.util.UUID;

/**
 * Input port for logout operations.
 *
 * <p>Provides single-session logout and logout-all (revoke all sessions)
 * capabilities. Cookie clearing is handled by the controller layer.
 */
public interface LogoutUseCase {

    /**
     * Revokes the current session.
     *
     * @param sessionId the session ID from the authenticated principal
     */
    void logout(UUID sessionId);

    /**
     * Revokes all active sessions for a user.
     *
     * @param userId the user ID from the authenticated principal
     */
    void logoutAll(UUID userId);
}
