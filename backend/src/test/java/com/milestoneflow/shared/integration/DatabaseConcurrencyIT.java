package com.milestoneflow.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that database unique constraints enforce correctness under concurrency.
 */
class DatabaseConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, email, email, "Test User", "{bcrypt}hash", "ACTIVE"
        );
        return id;
    }

    // ── Concurrent unique constraint tests ────────────────────────────────

    @Test
    void shouldEnforceFamilyGenerationUniqueUnderConcurrency() throws Exception {
        UUID userId = insertUser("concurrent-fg@example.com");
        UUID familyId = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    TransactionTemplate tx = new TransactionTemplate(txManager);
                    tx.execute(status -> {
                        try {
                            jdbc.update(
                                    "INSERT INTO auth_session (id, user_id, access_token_hash, refresh_token_hash, "
                                            + "session_family_id, refresh_generation, status, expires_at) "
                                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                    UUID.randomUUID(), userId,
                                    "access-" + familyId + "-" + idx,
                                    "refresh-" + familyId + "-" + idx,
                                    familyId, 0, "ACTIVE", expires
                            );
                            successCount.incrementAndGet();
                        } catch (DataAccessException e) {
                            failCount.incrementAndGet();
                        }
                        return null;
                    });
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    void shouldEnforceActiveOwnerUniqueUnderConcurrency() throws Exception {
        UUID wsId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO workspace (id, name, slug, default_currency, timezone, status, settings) "
                        + "VALUES (?, ?, ?, 'TWD', 'Asia/Taipei', 'ACTIVE', '{}')",
                wsId, "Concurrent Owner WS", "concurrent-owner-ws"
        );

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    String email = "conc-owner" + idx + "@example.com";
                    UUID userId = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO app_user (id, email, email_normalized, display_name, password_hash, status) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)",
                            userId, email, email, "User " + idx, "hash", "ACTIVE"
                    );
                    try {
                        jdbc.update(
                                "INSERT INTO workspace_membership (id, workspace_id, user_id, role, status) "
                                        + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE')",
                                UUID.randomUUID(), wsId, userId
                        );
                        successCount.incrementAndGet();
                    } catch (DataAccessException e) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }
}
