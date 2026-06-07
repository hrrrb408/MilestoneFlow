package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.model.AppUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 *
 * <p>Converts database unique-constraint violations for
 * {@code uk_app_user_email_normalized} into the application-level
 * {@link EmailAlreadyExistsException} at the infrastructure boundary.
 */
@Component
public class AppUserRepositoryAdapter implements AppUserRepository {

    private final SpringDataAppUserRepository delegate;

    AppUserRepositoryAdapter(SpringDataAppUserRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public AppUser save(AppUser user) {
        try {
            return delegate.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (ConstraintViolationMapper.isDuplicateEmail(ex)) {
                throw new EmailAlreadyExistsException();
            }
            throw ex;
        }
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
