package com.milestoneflow.identity.integration;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for concurrent refresh token rotation against PostgreSQL 17.
 *
 * <p>Verifies that concurrent refresh requests using the same token are
 * handled correctly under strict security policy: one succeeds, the other
 * triggers replay detection and family revocation.
 */
@DisplayName("Refresh Token Concurrency IT")
class RefreshTokenConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "concurrent-it@example.com";
    private static final String PASSWORD = "test-password-123";
    private String userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM verification_token WHERE user_id IN (SELECT id FROM app_user WHERE email_normalized = ?)", EMAIL.toLowerCase());
        jdbc.update("DELETE FROM app_user WHERE email_normalized = ?", EMAIL.toLowerCase());

        String encodedPassword = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
            INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status, locale, version, created_at, updated_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 'en', 0, now(), now())
            """, EMAIL, EMAIL.toLowerCase(), "Concurrency IT User", encodedPassword);

        userId = jdbc.queryForObject("SELECT id::text FROM app_user WHERE email_normalized = ?",
                String.class, EMAIL.toLowerCase());
    }

    private ResponseEntity<Map> doLogin() {
        var body = Map.of("email", EMAIL, "password", PASSWORD);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    private HttpHeaders cookiesFrom(ResponseEntity<?> response) {
        var headers = new HttpHeaders();
        response.getHeaders().get("Set-Cookie").forEach(c -> headers.add("Cookie", c.split(";")[0]));
        return headers;
    }

    @Nested
    @DisplayName("Concurrent refresh with same token")
    class ConcurrentRefresh {

        @Test
        @DisplayName("one succeeds, one replay — no ACTIVE sessions remain")
        void concurrentRefreshStrictPolicy() throws Exception {
            var loginResponse = doLogin();
            HttpHeaders cookies = cookiesFrom(loginResponse);

            String familyId = jdbc.queryForObject(
                    "SELECT session_family_id::text FROM auth_session WHERE user_id = ?::uuid AND status = 'ACTIVE'",
                    String.class, userId);

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startBarrier = new CountDownLatch(1);
            CountDownLatch readyBarrier = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger replayCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        readyBarrier.countDown();
                        startBarrier.await(5, TimeUnit.SECONDS);

                        ResponseEntity<Map> response = restTemplate.exchange(
                                "/auth/refresh", HttpMethod.POST,
                                new HttpEntity<>(cookies), Map.class);

                        if (response.getStatusCode() == HttpStatus.OK) {
                            successCount.incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED
                                && "AUTH_REFRESH_TOKEN_REUSED".equals(response.getBody().get("code"))) {
                            replayCount.incrementAndGet();
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

            // Under strict policy: exactly 1 success, exactly 1 replay, 0 errors
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(replayCount.get()).isEqualTo(1);
            assertThat(errorCount.get()).isEqualTo(0);

            // No ACTIVE sessions remain in the family
            Integer activeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE session_family_id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class, familyId);
            assertThat(activeCount).isEqualTo(0);

            // At least one session should have REFRESH_REPLAY_DETECTED
            Integer replayDetected = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM auth_session WHERE session_family_id = ?::uuid AND revoke_reason = 'REFRESH_REPLAY_DETECTED'",
                    Integer.class, familyId);
            assertThat(replayDetected).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("no deadlocks during concurrent refresh")
        void noDeadlocks() throws Exception {
            var loginResponse = doLogin();
            HttpHeaders cookies = cookiesFrom(loginResponse);

            int threadCount = 3;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startBarrier = new CountDownLatch(1);
            CountDownLatch readyBarrier = new CountDownLatch(threadCount);

            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger completedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        readyBarrier.countDown();
                        startBarrier.await(5, TimeUnit.SECONDS);

                        restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                                new HttpEntity<>(cookies), Map.class);
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
