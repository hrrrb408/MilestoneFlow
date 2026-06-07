package com.milestoneflow.identity.integration;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.identity.application.port.out.VerificationTokenRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.identity.domain.model.VerificationToken;
import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.identity.domain.type.VerificationTokenPurpose;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for registration and email verification against PostgreSQL 17.
 */
class EmailVerificationConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private Clock clock;

    @Autowired
    private EmailVerificationProperties properties;

    @Autowired
    private TokenHasher tokenHasher;

    @Nested
    @DisplayName("Concurrent confirmation")
    class ConcurrentConfirmation {

        @Test
        @DisplayName("only one concurrent confirmation succeeds for same token")
        void onlyOneSucceeds() throws InterruptedException {
            String rawToken = "concurrent-token-" + UUID.randomUUID();
            String tokenHash = tokenHasher.hash(rawToken);

            UUID userId = idGenerator.nextId();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "conc" + suffix + "@test.com";
            AppUser user = AppUser.create(userId, email, email.toLowerCase(),
                    "Concurrent User", passwordEncoder.encode("password123"), "en");
            appUserRepository.save(user);

            VerificationToken token = VerificationToken.create(
                    idGenerator.nextId(), userId, VerificationTokenPurpose.EMAIL_VERIFICATION,
                    tokenHash, Instant.now(clock).plus(properties.tokenTtl()));
            verificationTokenRepository.save(token);

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("X-Request-Id", UUID.randomUUID().toString());
                        var response = restTemplate.postForEntity(
                                "/auth/email-verification/confirm",
                                new HttpEntity<>(Map.of("token", rawToken), headers),
                                Map.class
                        );
                        if (response.getStatusCode().is2xxSuccessful()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            // Exactly one success, one failure
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);

            // User should be ACTIVE
            AppUser loadedUser = appUserRepository.findById(userId).orElseThrow();
            assertThat(loadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

            // Token should be used exactly once
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    userId, VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).getUsedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Concurrent registration")
    class ConcurrentRegistration {

        @Test
        @DisplayName("concurrent registration with same email only creates one user")
        void onlyOneRegistrationSucceeds() throws InterruptedException {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "concreg" + suffix + "@test.com";

            int threadCount = 2;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("X-Request-Id", UUID.randomUUID().toString());
                        var response = restTemplate.postForEntity(
                                "/auth/register",
                                new HttpEntity<>(Map.of(
                                        "email", email,
                                        "displayName", "Concurrent Reg " + threadIndex,
                                        "password", "password123"
                                ), headers),
                                Map.class
                        );
                        if (response.getStatusCode().value() == 201) {
                            successCount.incrementAndGet();
                        } else if (response.getStatusCode().value() == 409) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Connection errors etc.
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            // At most one success, at least one conflict or error
            assertThat(successCount.get()).isLessThanOrEqualTo(1);
            assertThat(successCount.get() + conflictCount.get()).isGreaterThanOrEqualTo(1);

            // Only one user in database
            var user = appUserRepository.findByEmailNormalized(email.toLowerCase());
            assertThat(user).isPresent();

            // Only one verification token
            List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndPurpose(
                    user.get().getId(), VerificationTokenPurpose.EMAIL_VERIFICATION);
            assertThat(tokens).hasSize(1);
        }
    }
}
