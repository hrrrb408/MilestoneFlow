package com.milestoneflow.identity.application.port.in;

import com.milestoneflow.identity.application.command.RegisterUserCommand;
import com.milestoneflow.identity.application.result.RegistrationResult;

/**
 * Input port for user registration.
 */
public interface RegisterUserUseCase {

    /**
     * Registers a new user.
     *
     * @param command registration details
     * @return registration result with user ID, email, and status
     */
    RegistrationResult register(RegisterUserCommand command);
}
