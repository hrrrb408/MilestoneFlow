package com.milestoneflow.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configures password encoding using Spring Security's {@link PasswordEncoder}.
 *
 * <p>Uses {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()}
 * which defaults to BCrypt. The encoded result includes an algorithm identifier
 * prefix (e.g., {@code {bcrypt}$2a$10$...}).
 *
 * <p>Note: Only {@code spring-security-crypto} is used, not the full
 * Spring Security starter, to avoid enabling default security filters.
 */
@Configuration
public class PasswordEncodingConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
