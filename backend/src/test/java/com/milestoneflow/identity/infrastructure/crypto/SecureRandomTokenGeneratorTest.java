package com.milestoneflow.identity.infrastructure.crypto;

import com.milestoneflow.identity.application.service.SecretToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecureRandomTokenGenerator")
class SecureRandomTokenGeneratorTest {

    private SecureRandomTokenGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SecureRandomTokenGenerator();
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("produces non-null token")
        void nonNullToken() {
            SecretToken token = generator.generate();
            assertThat(token).isNotNull();
            assertThat(token.value()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("produces URL-safe Base64 characters only")
        void urlSafeBase64() {
            SecretToken token = generator.generate();
            String value = token.value();
            // URL-safe Base64: A-Z, a-z, 0-9, -, _ (no padding =)
            assertThat(value).matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("produces no padding characters")
        void noPadding() {
            for (int i = 0; i < 100; i++) {
                SecretToken token = generator.generate();
                assertThat(token.value()).doesNotContain("=");
            }
        }

        @Test
        @DisplayName("produces sufficiently long token (32 bytes = 43 chars in base64url)")
        void sufficientLength() {
            SecretToken token = generator.generate();
            // 32 bytes -> Base64 URL without padding = 43 characters
            assertThat(token.value().length()).isGreaterThanOrEqualTo(43);
        }

        @Test
        @DisplayName("10000 generated tokens are all unique")
        void uniqueTokens() {
            Set<String> tokens = new HashSet<>();
            for (int i = 0; i < 10_000; i++) {
                tokens.add(generator.generate().value());
            }
            assertThat(tokens).hasSize(10_000);
        }

        @Test
        @DisplayName("SecretToken toString returns [REDACTED]")
        void toStringRedacted() {
            SecretToken token = generator.generate();
            assertThat(token.toString()).isEqualTo("[REDACTED]");
            assertThat(token.toString()).doesNotContain(token.value());
        }

        @Test
        @DisplayName("concurrent generation is thread-safe")
        void concurrentGeneration() throws InterruptedException {
            int threadCount = 16;
            int tokensPerThread = 500;
            Set<String> allTokens = ConcurrentHashMap.newKeySet();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < tokensPerThread; i++) {
                            allTokens.add(generator.generate().value());
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

            int expectedTotal = threadCount * tokensPerThread;
            assertThat(allTokens).hasSize(expectedTotal);
        }
    }
}
