package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.RefreshTokenCommand;
import com.milestoneflow.identity.application.port.in.RefreshTokenUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.AuthSessionRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.result.RefreshTokenResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.AuthSessionRevokedException;
import com.milestoneflow.identity.domain.exception.RefreshTokenExpiredException;
import com.milestoneflow.identity.domain.exception.RefreshTokenInvalidException;
import com.milestoneflow.identity.domain.exception.RefreshTokenReusedException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.AuthSession;
import com.milestoneflow.identity.domain.type.AuthSessionStatus;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.infrastructure.config.AuthTokenProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Application service for refresh token rotation.
 *
 * <p>Refresh flow:
 * <ol>
 *   <li>Hash the raw refresh token from the cookie</li>
 *   <li>Find session by hash with PESSIMISTIC_WRITE lock</li>
 *   <li>Validate session status and expiration</li>
 *   <li>Detect replay (already-rotated token reuse)</li>
 *   <li>Validate user status</li>
 *   <li>Revoke old session as REFRESH_ROTATED</li>
 *   <li>Create new session in same family with generation + 1</li>
 *   <li>Return new raw tokens for cookie setting</li>
 * </ol>
 *
 * <p>Replay detection: if an already-rotated token is reused, the entire
 * session family is revoked. No new tokens are issued.
 */
@Service
public class RefreshTokenService implements RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final AuthSessionRepository authSessionRepository;
    private final AppUserRepository appUserRepository;
    private final AuthSessionFamilyRevocationService familyRevocationService;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final AuthTokenProperties tokenProperties;

    public RefreshTokenService(AuthSessionRepository authSessionRepository,
                               AppUserRepository appUserRepository,
                               AuthSessionFamilyRevocationService familyRevocationService,
                               SecureTokenGenerator tokenGenerator,
                               TokenHasher tokenHasher,
                               IdGenerator idGenerator,
                               Clock clock,
                               AuthTokenProperties tokenProperties) {
        this.authSessionRepository = authSessionRepository;
        this.appUserRepository = appUserRepository;
        this.familyRevocationService = familyRevocationService;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.tokenProperties = tokenProperties;
    }

    @Override
    @Transactional
    public RefreshTokenResult refresh(RefreshTokenCommand command) {
        // 1. Hash the raw refresh token
        String refreshTokenHash = tokenHasher.hash(command.getRawRefreshToken());

        // 2. Lock the session row
        Optional<AuthSession> sessionOpt =
                authSessionRepository.findByRefreshTokenHashForUpdate(refreshTokenHash);

        if (sessionOpt.isEmpty()) {
            log.debug("Refresh failed: session not found for token hash");
            throw new RefreshTokenInvalidException();
        }

        AuthSession session = sessionOpt.get();
        Instant now = Instant.now(clock);

        // 3. Check for replay: session was already rotated
        if (session.isRefreshRotated()) {
            log.warn("Refresh replay detected: sessionFamilyId={}", session.getSessionFamilyId());
            familyRevocationService.revokeEntireFamily(session.getSessionFamilyId(), now);
            throw new RefreshTokenReusedException();
        }

        // 4. Check session status
        if (session.getStatus() == AuthSessionStatus.EXPIRED) {
            throw new RefreshTokenExpiredException();
        }
        if (session.getStatus() == AuthSessionStatus.REVOKED) {
            throw new AuthSessionRevokedException();
        }

        // 5. Check refresh token expiration
        if (session.isRefreshExpired(now)) {
            throw new RefreshTokenExpiredException();
        }

        // 6. Validate user
        Optional<AppUser> userOpt = appUserRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("Refresh failed: user not found userId={}", session.getUserId());
            throw new RefreshTokenInvalidException();
        }

        AppUser user = userOpt.get();
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new RefreshTokenInvalidException();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AccountDisabledException();
        }

        // 7. Rotate: revoke old session
        session.revokeAsRotated(now);
        authSessionRepository.save(session);

        // 8. Create new session
        SecretToken rawAccessToken = tokenGenerator.generate();
        SecretToken rawRefreshToken = tokenGenerator.generate();
        String newAccessHash = tokenHasher.hash(rawAccessToken.value());
        String newRefreshHash = tokenHasher.hash(rawRefreshToken.value());

        Instant accessExpiresAt = now.plus(tokenProperties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(tokenProperties.refreshTokenTtl());

        AuthSession newSession = AuthSession.create(
                idGenerator.nextId(),
                user.getId(),
                newAccessHash,
                newRefreshHash,
                session.getSessionFamilyId(),
                session.getRefreshGeneration() + 1,
                accessExpiresAt,
                refreshExpiresAt,
                null,
                null
        );

        authSessionRepository.save(newSession);

        log.info("Refresh succeeded: userId={}, familyId={}, newGeneration={}",
                user.getId(), session.getSessionFamilyId(), newSession.getRefreshGeneration());

        return new RefreshTokenResult(rawAccessToken, rawRefreshToken);
    }
}
