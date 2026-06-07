package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.LoginCommand;
import com.milestoneflow.identity.application.result.LoginResult;

/**
 * Input port for user login.
 */
public interface LoginUseCase {

    /**
     * Authenticates a user and creates an auth session.
     *
     * <p>Login flow:
     * <ol>
     *   <li>Normalize email</li>
     *   <li>Find user by normalized email</li>
     *   <li>Validate password</li>
     *   <li>Check user status (must be ACTIVE)</li>
     *   <li>Generate access and refresh tokens</li>
     *   <li>Hash tokens and create AuthSession</li>
     *   <li>Update lastLoginAt</li>
     *   <li>Return result with raw tokens for cookie setting</li>
     * </ol>
     *
     * @param command login credentials
     * @return login result containing user data and raw tokens
     * @throws com.milestoneflow.identity.domain.exception.InvalidCredentialsException for wrong email/password
     * @throws com.milestoneflow.identity.domain.exception.EmailNotVerifiedException for unverified users
     * @throws com.milestoneflow.identity.domain.exception.AccountDisabledException for disabled users
     */
    LoginResult login(LoginCommand command);
}
