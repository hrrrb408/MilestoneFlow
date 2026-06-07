package com.milestoneflow.identity.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordEncodingConfiguration")
class PasswordEncodingConfigurationTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Nested
    @DisplayName("PasswordEncoder")
    class EncoderTests {

        @Test
        @DisplayName("encoded result is not equal to raw password")
        void encodedNotEqualRaw() {
            String raw = "test-password";
            String encoded = passwordEncoder.encode(raw);
            assertThat(encoded).isNotEqualTo(raw);
        }

        @Test
        @DisplayName("encoded result contains {bcrypt} prefix")
        void containsBcryptPrefix() {
            String raw = "test-password";
            String encoded = passwordEncoder.encode(raw);
            assertThat(encoded).startsWith("{bcrypt}");
        }

        @Test
        @DisplayName("matches returns true for correct password")
        void matchesCorrectPassword() {
            String raw = "test-password";
            String encoded = passwordEncoder.encode(raw);
            assertThat(passwordEncoder.matches(raw, encoded)).isTrue();
        }

        @Test
        @DisplayName("matches returns false for wrong password")
        void doesNotMatchWrongPassword() {
            String raw = "test-password";
            String encoded = passwordEncoder.encode(raw);
            assertThat(passwordEncoder.matches("wrong-password", encoded)).isFalse();
        }

        @Test
        @DisplayName("same password produces different hashes")
        void differentHashesForSamePassword() {
            String raw = "test-password";
            String encoded1 = passwordEncoder.encode(raw);
            String encoded2 = passwordEncoder.encode(raw);
            assertThat(encoded1).isNotEqualTo(encoded2);
        }
    }
}
