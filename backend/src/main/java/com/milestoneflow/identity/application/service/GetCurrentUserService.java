package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.port.in.GetCurrentUserUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.result.CurrentUserResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.type.UserStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application service for fetching the current authenticated user.
 *
 * <p>Loads the user by ID and returns safe, non-sensitive data.
 * Throws an exception if the user is not found or not active.
 */
@Service
public class GetCurrentUserService implements GetCurrentUserUseCase {

    private final AppUserRepository userRepository;

    public GetCurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public CurrentUserResult getCurrentUser(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountDisabledException());

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountDisabledException();
        }

        return new CurrentUserResult(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().name()
        );
    }
}
