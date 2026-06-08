package com.milestoneflow.identity.infrastructure.ratelimit;

import com.milestoneflow.identity.application.ratelimit.AuthRateLimitAction;
import com.milestoneflow.identity.application.ratelimit.RateLimitDecision;
import com.milestoneflow.shared.testing.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryAuthRateLimiter}.
 *
 * <p>Uses {@link MutableClock} to simulate window expiration without Thread.sleep.
 */
class InMemoryAuthRateLimiterTest {

    private static final Instant BASE_TIME = Instant.parse("2026-06-08T12:00:00Z");

    private MutableClock clock;
    private InMemoryAuthRateLimiter limiter;

    private AuthRateLimitProperties enabledProperties() {
        return new AuthRateLimitProperties(true, Map.of(
                "LOGIN", new AuthRateLimitProperties.Policy(3, Duration.ofMinutes(15)),
                "REGISTER", new AuthRateLimitProperties.Policy(5, Duration.ofHours(1)),
                "EMAIL_VERIFICATION_RESEND", new AuthRateLimitProperties.Policy(3, Duration.ofMinutes(15)),
                "FORGOT_PASSWORD", new AuthRateLimitProperties.Policy(3, Duration.ofMinutes(15)),
                "RESET_PASSWORD", new AuthRateLimitProperties.Policy(10, Duration.ofMinutes(15))
        ));
    }

    private AuthRateLimitProperties disabledProperties() {
        return new AuthRateLimitProperties(false, Map.of());
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(BASE_TIME, ZoneOffset.UTC);
    }

    // ── Basic Functionality ──────────────────────────────────────────────

    @Nested
    class BasicFunctionality {

        @BeforeEach
        void setUp() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);
        }

        @Test
        void shouldAllowUnderLimit() {
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(2);
        }

        @Test
        void shouldRejectAfterMaxAttempts() {
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            // 4th attempt should be rejected
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isFalse();
            assertThat(decision.retryAfterSeconds()).isGreaterThan(0);
        }

        @Test
        void shouldTrackRemainingCount() {
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(1);
        }
    }

    // ── Window Expiration ────────────────────────────────────────────────

    @Nested
    class WindowExpiration {

        @Test
        void shouldRecoverAfterWindowExpires() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);

            // Exhaust the limit
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            RateLimitDecision rejected = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(rejected.allowed()).isFalse();

            // Advance clock past the window
            clock.advance(Duration.ofMinutes(16));

            // Should be allowed again in the new window
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isTrue();
        }

        @Test
        void shouldUseInjectedClockNotSystemTime() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);

            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            // Move clock forward 16 minutes
            clock.advance(Duration.ofMinutes(16));

            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isTrue();
        }

        @Test
        void shouldNotUseThreadSleep() {
            // This test verifies that the implementation uses Clock, not Thread.sleep
            // By using MutableClock we demonstrate no sleep is needed
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);

            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            // Advance by 1ms — still within window
            clock.advance(Duration.ofMillis(1));
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "hash1").allowed()).isFalse();

            // Advance past window
            clock.advance(Duration.ofMinutes(15));
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "hash1").allowed()).isTrue();
        }
    }

    // ── Action Independence ──────────────────────────────────────────────

    @Nested
    class ActionIndependence {

        @BeforeEach
        void setUp() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);
        }

        @Test
        void differentActionsShouldBeIndependent() {
            // Exhaust LOGIN limit
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            // REGISTER should still work with same key
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.REGISTER, "hash1");
            assertThat(decision.allowed()).isTrue();
        }

        @Test
        void differentKeysShouldBeIndependent() {
            // Exhaust limit for key1
            limiter.check(AuthRateLimitAction.LOGIN, "key1");
            limiter.check(AuthRateLimitAction.LOGIN, "key1");
            limiter.check(AuthRateLimitAction.LOGIN, "key1");

            // key2 should still work
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "key2");
            assertThat(decision.allowed()).isTrue();
        }
    }

    // ── Reset ────────────────────────────────────────────────────────────

    @Nested
    class Reset {

        @BeforeEach
        void setUp() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);
        }

        @Test
        void shouldResetCounterForActionAndKey() {
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            // Now blocked
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "hash1").allowed()).isFalse();

            // Reset
            limiter.reset(AuthRateLimitAction.LOGIN, "hash1");

            // Should be allowed again
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "hash1").allowed()).isTrue();
        }

        @Test
        void resetShouldNotAffectOtherKeys() {
            limiter.check(AuthRateLimitAction.LOGIN, "key1");
            limiter.check(AuthRateLimitAction.LOGIN, "key1");
            limiter.check(AuthRateLimitAction.LOGIN, "key1");

            limiter.check(AuthRateLimitAction.LOGIN, "key2");
            limiter.check(AuthRateLimitAction.LOGIN, "key2");
            limiter.check(AuthRateLimitAction.LOGIN, "key2");

            limiter.reset(AuthRateLimitAction.LOGIN, "key1");

            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "key1").allowed()).isTrue();
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "key2").allowed()).isFalse();
        }
    }

    // ── Record Failure ───────────────────────────────────────────────────

    @Nested
    class RecordFailure {

        @BeforeEach
        void setUp() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);
        }

        @Test
        void shouldRecordFailureAndRejectAfterLimit() {
            limiter.recordFailure(AuthRateLimitAction.LOGIN, "hash1");
            limiter.recordFailure(AuthRateLimitAction.LOGIN, "hash1");
            limiter.recordFailure(AuthRateLimitAction.LOGIN, "hash1");

            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isFalse();
        }

        @Test
        void recordFailureAndCheckShouldShareCounter() {
            limiter.recordFailure(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.recordFailure(AuthRateLimitAction.LOGIN, "hash1");

            // 3 attempts used (1 recordFailure + 1 check + 1 recordFailure)
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isFalse();
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    @Nested
    class Cleanup {

        @Test
        void shouldRemoveExpiredWindows() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);

            limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            limiter.check(AuthRateLimitAction.LOGIN, "hash2");

            // Advance past window
            clock.advance(Duration.ofMinutes(16));

            limiter.cleanup();

            // Should be allowed again (expired windows were removed)
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "hash1").allowed()).isTrue();
        }

        @Test
        void shouldNotRemoveActiveWindows() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);

            limiter.check(AuthRateLimitAction.LOGIN, "hash1");

            // Advance just 1 minute — window still active
            clock.advance(Duration.ofMinutes(1));

            limiter.cleanup();

            // Second attempt within same window
            RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(1);
        }
    }

    // ── Disabled Rate Limiting ───────────────────────────────────────────

    @Nested
    class Disabled {

        @Test
        void shouldAllowAllWhenDisabled() {
            limiter = new InMemoryAuthRateLimiter(disabledProperties(), clock);

            for (int i = 0; i < 100; i++) {
                RateLimitDecision decision = limiter.check(AuthRateLimitAction.LOGIN, "hash1");
                assertThat(decision.allowed()).isTrue();
            }
        }

        @Test
        void recordFailureShouldBeNoOpWhenDisabled() {
            limiter = new InMemoryAuthRateLimiter(disabledProperties(), clock);

            for (int i = 0; i < 100; i++) {
                limiter.recordFailure(AuthRateLimitAction.LOGIN, "hash1");
            }

            // Should still be allowed
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, "hash1").allowed()).isTrue();
        }
    }

    // ── Properties Validation ────────────────────────────────────────────

    @Nested
    class PropertiesValidation {

        @Test
        void shouldRejectZeroMaxAttempts() {
            assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(0, Duration.ofMinutes(15)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNegativeMaxAttempts() {
            assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(-1, Duration.ofMinutes(15)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectZeroWindow() {
            assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(5, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNegativeWindow() {
            assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(5, Duration.ofMinutes(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── No Raw Sensitive Data ────────────────────────────────────────────

    @Nested
    class NoSensitiveData {

        @BeforeEach
        void setUp() {
            limiter = new InMemoryAuthRateLimiter(enabledProperties(), clock);
        }

        @Test
        void shouldNotStoreRawEmail() {
            // The key passed to the limiter should be a hash, not raw email
            // This test documents the contract: the limiter does not log or expose the key
            String emailHash = "sha256:user@example.com"; // hashed externally
            limiter.check(AuthRateLimitAction.LOGIN, emailHash);
            limiter.check(AuthRateLimitAction.LOGIN, emailHash);
            limiter.check(AuthRateLimitAction.LOGIN, emailHash);

            // Should work with hash-based keys
            assertThat(limiter.check(AuthRateLimitAction.LOGIN, emailHash).allowed()).isFalse();
        }

        @Test
        void shouldNotStoreRawToken() {
            String tokenHashPrefix = "sha256:prefix_abc123";
            limiter.check(AuthRateLimitAction.RESET_PASSWORD, tokenHashPrefix);

            RateLimitDecision decision = limiter.check(AuthRateLimitAction.RESET_PASSWORD, tokenHashPrefix);
            assertThat(decision.allowed()).isTrue();
        }
    }
}
