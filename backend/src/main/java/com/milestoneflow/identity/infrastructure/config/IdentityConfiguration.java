package com.milestoneflow.identity.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Identity module configuration.
 *
 * <p>Enables configuration properties for email verification, auth tokens, and cookies.
 */
@Configuration
@EnableConfigurationProperties({
        EmailVerificationProperties.class,
        AuthTokenProperties.class,
        AuthCookieProperties.class,
        PasswordResetProperties.class
})
public class IdentityConfiguration {
}
