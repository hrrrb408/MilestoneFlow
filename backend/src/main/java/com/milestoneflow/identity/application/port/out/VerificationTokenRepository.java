package com.milestoneflow.identity.application.port.out;

import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for {@link VerificationToken} persistence.
 *
 * <p>This interface belongs to the application layer and must not expose
 * Spring Data types. The concrete implementation lives in the infrastructure
 * layer as an adapter.
 *
 * <p>Query methods align with existing database indexes:
 * <ul>
 *   <li>{@code token_hash} — unique index</li>
 *   <li>{@code user_id + purpose} — composite index</li>
 * </ul>
 *
 * <p>No time-based filtering is performed at the database level.
 * Token validity is determined by the application layer using an injected
 * {@link java.time.Clock}.
 */
public interface VerificationTokenRepository {

    VerificationToken save(VerificationToken token);

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash,
                                                          VerificationTokenPurpose purpose);

    List<VerificationToken> findByUserIdAndPurpose(UUID userId,
                                                   VerificationTokenPurpose purpose);

    /**
     * Finds a token by hash and purpose with a pessimistic write lock.
     *
     * <p>Used during email verification confirmation to prevent concurrent
     * token reuse. The lock is held within the caller's transaction.
     *
     * @param tokenHash SHA-256 hash of the raw token
     * @param purpose   the token purpose
     * @return the token if found, empty otherwise
     */
    Optional<VerificationToken> findByTokenHashAndPurposeForUpdate(String tokenHash,
                                                                    VerificationTokenPurpose purpose);

    /**
     * Deletes all unused tokens for the given user and purpose.
     *
     * <p>Used during resend to invalidate old EMAIL_VERIFICATION tokens.
     * Only deletes tokens where {@code used_at} is null.
     *
     * @param userId  the user ID
     * @param purpose the token purpose to target
     */
    void deleteUnusedByUserIdAndPurpose(UUID userId, VerificationTokenPurpose purpose);
}
