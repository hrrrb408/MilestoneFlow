package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 */
@Component
public class AuthSessionRepositoryAdapter implements AuthSessionRepository {

    private final SpringDataAuthSessionRepository delegate;

    AuthSessionRepositoryAdapter(SpringDataAuthSessionRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public AuthSession save(AuthSession session) {
        return delegate.save(session);
    }

    @Override
    public Optional<AuthSession> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<AuthSession> findByAccessTokenHash(String accessTokenHash) {
        return delegate.findByAccessTokenHash(accessTokenHash);
    }

    @Override
    public Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash) {
        return delegate.findByRefreshTokenHash(refreshTokenHash);
    }

    @Override
    public List<AuthSession> findByUserIdAndStatus(UUID userId, AuthSessionStatus status) {
        return delegate.findByUserIdAndStatus(userId, status);
    }

    @Override
    public List<AuthSession> findBySessionFamilyId(UUID sessionFamilyId) {
        return delegate.findBySessionFamilyId(sessionFamilyId);
    }
}
