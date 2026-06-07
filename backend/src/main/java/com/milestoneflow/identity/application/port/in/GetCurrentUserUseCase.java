package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.result.CurrentUserResult;

import java.util.UUID;

/**
 * Input port for fetching the current authenticated user.
 */
public interface GetCurrentUserUseCase {

    /**
     * Retrieves the current user by their ID.
     *
     * @param userId the authenticated user's ID
     * @return current user result with safe, non-sensitive data
     * @throws com.milestoneflow.identity.domain.exception.AccountDisabledException if user is disabled
     */
    CurrentUserResult getCurrentUser(UUID userId);
}
