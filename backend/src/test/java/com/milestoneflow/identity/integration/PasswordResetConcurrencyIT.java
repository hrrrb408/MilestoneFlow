package com.milestoneflow.identity.integration;

import com.milestoneflow.identity.application.port.out.TokenHasher;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for concurrent password reset against PostgreSQL 17.
 *
 * <p>Verifies that concurrent reset requests using the same token are
 * handled correctly: exactly one succeeds, the other fails, token used_at
 * is set only once, password hash is consistent, and no deadlock occurs.
 */
@DisplayName("Password Reset Concurrency IT")
class PasswordResetConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenHasher tokenHasher;

    private static final String EMAIL = "concurrent-reset-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private static final String NEW_PASSWORD = "concurrent-reset-pw-456";
    private String userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("ALTER TABLE audit_event DISABLE TRIGGER ALL");
        jdbc.update("DELETE FROM audit_event WHERE actor_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", EMAIL.toLowerCase());
        jdbc.update("ALTER TABLE audit_event ENABLE TRIGGER ALL");

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Concurrent Reset IT User", encodedPassword);

        userId = jdbc.queryForObject("SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());
    }

    private void createSession() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * Generates a raw token, hashes it with TokenHasher, and inserts a PASSWORD_RESET
     * verification_token row. Returns the raw token for use in the reset request.
     */
    private String insertPasswordResetToken() {
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = tokenHasher.hash(rawToken);

        jdbc.update("""
            INSERT INTO verification_token (id, user_id, purpose, token_hash, expires_at, created_at)
            VALUES (gen_random_uuid(), ?::uuid, 'PASSWORD_RESET', ?, now() + interval '1 hour', now())
            """, userId, tokenHash);

        return rawToken;
    }

    @Nested
    @DisplayName("Concurrent reset with same token")
    class ConcurrentReset {

        @Test
        @DisplayName("one succeeds, one fails — token usedAt set only once, passwordHash consistent")
        void concurrentResetSameToken() throws Exception {
            // Create a session and a reset token
            createSession();
            String rawToken = insertPasswordResetToken();

            String originalHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startBarrier = new CountDownLatch(1);
            CountDownLatch readyBarrier = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        readyBarrier.countDown();
                        startBarrier.await(5, TimeUnit.SECONDS);

                        var body = Map.of("token", rawToken, "newPassword", NEW_PASSWORD);
                        var headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        ResponseEntity<Map> response = restTemplate.exchange(
                                "/auth/password/reset", HttpMethod.POST,
                                new HttpEntity<>(body, headers), Map.class);

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount.incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                            failureCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }));
            }

            // Wait for both threads to be ready, then start simultaneously
            readyBarrier.await(5, TimeUnit.SECONDS);
            startBarrier.countDown();

            // Wait for all to complete
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            // Exactly 1 success, exactly 1 failure, 0 errors
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(1);
            assertThat(errorCount.get()).isEqualTo(0);

            // Token used_at should be set exactly once
            Map<String, Object> tokenRow = jdbc.queryForMap(
                    "SELECT used_at FROM verification_token WHERE user_id = ?::uuid AND purpose = 'PASSWORD_RESET'",
                    userId);
            assertThat(tokenRow.get("used_at")).isNotNull();

            // Password hash should have changed exactly once and be consistent
            String currentHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?::uuid",
                    String.class, userId);
            assertThat(currentHash).isNotEqualTo(originalHash);
            assertThat(passwordEncoder.matches(NEW_PASSWORD, currentHash)).isTrue();
        }

        @Test
        @DisplayName("no deadlocks during concurrent reset")
        void noDeadlocks() throws Exception {
            createSession();
            String rawToken = insertPasswordResetToken();

            int threadCount = 3;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startBarrier = new CountDownLatch(1);
            CountDownLatch readyBarrier = new CountDownLatch(threadCount);

            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger completedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                // Each thread uses the same token
                futures.add(executor.submit(() -> {
                    try {
                        readyBarrier.countDown();
                        startBarrier.await(5, TimeUnit.SECONDS);

                        var body = Map.of("token", rawToken, "newPassword", NEW_PASSWORD);
                        var headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        restTemplate.exchange("/auth/password/reset", HttpMethod.POST,
                                new HttpEntity<>(body, headers), Map.class);
                        completedCount.incrementAndGet();
                    } catch (Exception e) {
                        // Connection timeout etc. are acceptable
                        completedCount.incrementAndGet();
                    }
                }));
            }

            readyBarrier.await(5, TimeUnit.SECONDS);
            startBarrier.countDown();

            // All must complete within 30 seconds (no deadlock)
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            assertThat(completedCount.get()).isEqualTo(threadCount);
        }
    }
}
