package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.model.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Revokes all ACTIVE sessions in a session family.
 *
 * <p>Runs in a {@code REQUIRES_NEW} transaction so that the revocation
 * is committed independently of the calling transaction. This is critical
 * for replay detection: the calling {@code @Transactional} method will
 * throw {@code RefreshTokenReusedException} (a {@code RuntimeException}),
 * which causes Spring to roll back the outer transaction. Without a
 * separate inner transaction, the family revocation would also be rolled
 * back, defeating the security guarantee.
 */
@Service
public class AuthSessionFamilyRevocationService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionFamilyRevocationService.class);

    private final AuthSessionRepository authSessionRepository;

    public AuthSessionFamilyRevocationService(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    /**
     * Revokes all ACTIVE sessions in the given family with
     * {@code REFRESH_REPLAY_DETECTED} reason.
     *
     * <p>Commits in its own transaction regardless of the caller's outcome.
     *
     * @param sessionFamilyId the family to revoke
     * @param now             the revocation timestamp
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeEntireFamily(UUID sessionFamilyId, Instant now) {
        List<AuthSession> activeSessions =
                authSessionRepository.findActiveBySessionFamilyId(sessionFamilyId);

        for (AuthSession s : activeSessions) {
            s.revokeAsReplayDetected(now);
            authSessionRepository.save(s);
        }

        log.warn("Revoked {} sessions in family {} due to replay detection",
                activeSessions.size(), sessionFamilyId);
    }
}
