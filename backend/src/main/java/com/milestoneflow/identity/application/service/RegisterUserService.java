package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.command.RegisterUserCommand;
import com.milestoneflow.identity.application.event.EmailVerificationRequestedEvent;
import com.milestoneflow.identity.application.port.in.RegisterUserUseCase;
import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.SecureTokenGenerator;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.application.result.RegistrationResult;
import com.milestoneflow.identity.domain.exception.EmailAlreadyExistsException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.policy.EmailNormalizationResult;
import com.milestoneflow.identity.domain.policy.PasswordPolicy;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Application service for user registration.
 *
 * <p>Registration flow:
 * <ol>
 *   <li>Normalize email</li>
 *   <li>Validate password policy</li>
 *   <li>Check email uniqueness (fast fail)</li>
 *   <li>Encode password</li>
 *   <li>Create AppUser in PENDING_VERIFICATION state</li>
 *   <li>Generate secure token and hash</li>
 *   <li>Create VerificationToken with EMAIL_VERIFICATION purpose</li>
 *   <li>Save both in a single transaction</li>
 *   <li>Publish event for AFTER_COMMIT email delivery</li>
 * </ol>
 */
@Service
public class RegisterUserService implements RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserService.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final AppUserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EmailVerificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterUserService(AppUserRepository userRepository,
                               VerificationTokenRepository tokenRepository,
                               PasswordEncoder passwordEncoder,
                               SecureTokenGenerator tokenGenerator,
                               TokenHasher tokenHasher,
                               IdGenerator idGenerator,
                               Clock clock,
                               EmailVerificationProperties properties,
                               ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public RegistrationResult register(RegisterUserCommand command) {
        // 1. Normalize email
        EmailNormalizationResult emailResult = EmailNormalizationResult.normalize(command.getEmail());

        // 2. Validate password policy
        PasswordPolicy.validate(command.getPassword());

        // 3. Fast-fail: check if email already exists
        if (userRepository.existsByEmailNormalized(emailResult.normalizedEmail())) {
            throw new EmailAlreadyExistsException();
        }

        // 4. Encode password
        String encodedPassword = passwordEncoder.encode(command.getPassword());

        // 5. Create AppUser
        UUID userId = idGenerator.nextId();
        AppUser user = AppUser.create(
                userId,
                emailResult.displayEmail(),
                emailResult.normalizedEmail(),
                command.getDisplayName(),
                encodedPassword,
                DEFAULT_LOCALE.toLanguageTag()
        );

        // 6-7. Generate verification token
        SecretToken secretToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(secretToken.value());

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.tokenTtl());

        VerificationToken verificationToken = VerificationToken.create(
                idGenerator.nextId(),
                userId,
                VerificationTokenPurpose.EMAIL_VERIFICATION,
                tokenHash,
                expiresAt
        );

        // 8. Save both in transaction
        // Constraint violation for uk_app_user_email_normalized is handled
        // by AppUserRepositoryAdapter at the infrastructure boundary.
        userRepository.save(user);
        tokenRepository.save(verificationToken);

        log.info("Registration completed userId={}", userId);

        // 9. Publish event for AFTER_COMMIT email delivery
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(
                userId,
                emailResult.displayEmail(),
                command.getDisplayName(),
                secretToken.value(),
                DEFAULT_LOCALE
        ));

        return new RegistrationResult(userId, emailResult.displayEmail(), user.getStatus());
    }
}
