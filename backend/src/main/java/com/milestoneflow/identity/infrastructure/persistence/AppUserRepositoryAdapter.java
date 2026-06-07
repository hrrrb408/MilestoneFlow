package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 */
@Component
public class AppUserRepositoryAdapter implements AppUserRepository {

    private final SpringDataAppUserRepository delegate;

    AppUserRepositoryAdapter(SpringDataAppUserRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public AppUser save(AppUser user) {
        return delegate.save(user);
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<AppUser> findByEmailNormalized(String emailNormalized) {
        return delegate.findByEmailNormalized(emailNormalized);
    }

    @Override
    public boolean existsByEmailNormalized(String emailNormalized) {
        return delegate.existsByEmailNormalized(emailNormalized);
    }
}
