package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.RefreshTokenCommand;
import com.milestoneflow.identity.application.result.RefreshTokenResult;

/**
 * Input port for refresh token rotation.
 *
 * <p>Takes a raw refresh token from the cookie and, if valid,
 * rotates it: the old session is revoked, a new session is created
 * in the same family with generation + 1, and new raw tokens are
 * returned for cookie setting.
 */
public interface RefreshTokenUseCase {

    /**
     * Rotates a refresh token.
     *
     * @param command contains the raw refresh token from the cookie
     * @return result with new raw tokens for cookie setting
     * @throws com.milestoneflow.identity.domain.exception.RefreshTokenMissingException if no token provided
     * @throws com.milestoneflow.identity.domain.exception.RefreshTokenInvalidException if token not found
     * @throws com.milestoneflow.identity.domain.exception.RefreshTokenExpiredException if token expired
     * @throws com.milestoneflow.identity.domain.exception.RefreshTokenReusedException if replay detected
     * @throws com.milestoneflow.identity.domain.exception.AuthSessionRevokedException if session revoked
     * @throws com.milestoneflow.identity.domain.exception.AccountDisabledException if user disabled
     */
    RefreshTokenResult refresh(RefreshTokenCommand command);
}
