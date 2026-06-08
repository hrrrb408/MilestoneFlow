package com.milestoneflow.identity.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthTokenProperties")
class AuthTokenPropertiesTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("null access TTL defaults to 15 minutes")
        void nullAccessTtlDefaults() {
            var props = new AuthTokenProperties(null, Duration.ofDays(30));
            assertThat(props.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("null refresh TTL defaults to 30 days")
        void nullRefreshTtlDefaults() {
            var props = new AuthTokenProperties(Duration.ofMinutes(15), null);
            assertThat(props.refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("zero access TTL rejected")
        void zeroAccessTtlRejected() {
            assertThatThrownBy(() -> new AuthTokenProperties(Duration.ZERO, Duration.ofDays(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("access-token-ttl must be positive");
        }

        @Test
        @DisplayName("negative access TTL rejected")
        void negativeAccessTtlRejected() {
            assertThatThrownBy(() -> new AuthTokenProperties(Duration.ofMinutes(-1), Duration.ofDays(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("access-token-ttl must be positive");
        }

        @Test
        @DisplayName("zero refresh TTL rejected")
        void zeroRefreshTtlRejected() {
            assertThatThrownBy(() -> new AuthTokenProperties(Duration.ofMinutes(15), Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refresh-token-ttl must be positive");
        }

        @Test
        @DisplayName("refresh TTL less than access TTL rejected")
        void refreshLessThanAccessRejected() {
            assertThatThrownBy(() -> new AuthTokenProperties(Duration.ofMinutes(30), Duration.ofMinutes(15)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refresh-token-ttl must be >= access-token-ttl");
        }

        @Test
        @DisplayName("equal TTLs accepted")
        void equalTtlsAccepted() {
            var props = new AuthTokenProperties(Duration.ofMinutes(15), Duration.ofMinutes(15));
            assertThat(props.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(props.refreshTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        }
    }
}
