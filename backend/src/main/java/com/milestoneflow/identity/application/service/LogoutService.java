package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.port.in.LogoutUseCase;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionRevokeReason;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for logout operations.
 *
 * <p>Logout revokes the current session. Logout-all revokes every active
 * session for the user. Neither deletes sessions from the database.
 *
 * <p>Cookie clearing is handled by the controller layer, not this service.
 */
@Service
public class LogoutService implements LogoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public LogoutService(AuthSessionRepository authSessionRepository, Clock clock) {
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void logout(UUID sessionId) {
        Instant now = Instant.now(clock);

        Optional<AuthSession> sessionOpt = authSessionRepository.findByIdForUpdate(sessionId);
        if (sessionOpt.isEmpty()) {
            log.debug("Logout failed: session not found sessionId={}", sessionId);
            throw new AuthSessionRevokedException();
        }

        AuthSession session = sessionOpt.get();
        if (session.getStatus() != AuthSessionStatus.ACTIVE) {
            log.debug("Logout failed: session not active sessionId={} status={}",
                    sessionId, session.getStatus());
            throw new AuthSessionRevokedException();
        }

        session.revoke(now, AuthSessionRevokeReason.LOGOUT);
        authSessionRepository.save(session);

        log.info("Logout succeeded: sessionId={}", sessionId);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {
        Instant now = Instant.now(clock);
        authSessionRepository.revokeAllByUserId(userId, now, AuthSessionRevokeReason.LOGOUT_ALL);

        log.info("Logout-all succeeded: userId={}", userId);
    }
}
