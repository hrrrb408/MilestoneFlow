package com.milestoneflow.identity.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sha256TokenHasher")
class Sha256TokenHasherTest {

    private Sha256TokenHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new Sha256TokenHasher();
    }

    @Nested
    @DisplayName("hash()")
    class Hash {

        @Test
        @DisplayName("produces 64-character hex string")
        void produces64CharHex() {
            String hash = hasher.hash("test-token");
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("is deterministic: same input produces same hash")
        void deterministic() {
            String input = "deterministic-test-token";
            String hash1 = hasher.hash(input);
            String hash2 = hasher.hash(input);
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("different inputs produce different hashes")
        void differentInputsDifferentHashes() {
            String hash1 = hasher.hash("token-a");
            String hash2 = hasher.hash("token-b");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("hash does not contain the raw token")
        void hashDoesNotContainRawToken() {
            String rawToken = "my-secret-token-value";
            String hash = hasher.hash(rawToken);
            assertThat(hash).doesNotContain(rawToken);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            String hash = hasher.hash("");
            assertThat(hash).hasSize(64);
        }

        @Test
        @DisplayName("handles Unicode input")
        void handlesUnicode() {
            String hash = hasher.hash("验证令牌");
            assertThat(hash).hasSize(64);
        }

        @Test
        @DisplayName("concurrent calls are thread-safe")
        void concurrentHashing() throws InterruptedException {
            int threadCount = 16;
            int hashesPerThread = 500;
            AtomicInteger mismatchCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            String input = "concurrent-test-token";

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String expected = hasher.hash(input);
                        for (int i = 0; i < hashesPerThread; i++) {
                            String hash = hasher.hash(input);
                            if (!hash.equals(expected)) {
                                mismatchCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            assertThat(mismatchCount.get()).isZero();
        }
    }
}
