package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
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
public class VerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final SpringDataVerificationTokenRepository delegate;

    VerificationTokenRepositoryAdapter(SpringDataVerificationTokenRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        return delegate.save(token);
    }

    @Override
    public Optional<VerificationToken> findByTokenHash(String tokenHash) {
        return delegate.findByTokenHash(tokenHash);
    }

    @Override
    public Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash,
                                                                  VerificationTokenPurpose purpose) {
        return delegate.findByTokenHashAndPurpose(tokenHash, purpose);
    }

    @Override
    public List<VerificationToken> findByUserIdAndPurpose(UUID userId,
                                                          VerificationTokenPurpose purpose) {
        return delegate.findByUserIdAndPurpose(userId, purpose);
    }
}
