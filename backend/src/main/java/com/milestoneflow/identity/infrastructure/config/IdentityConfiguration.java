package com.milestoneflow.identity.infrastructure.config;

import com.milestoneflow.identity.infrastructure.ratelimit.AuthRateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Identity module configuration.
 *
 * <p>Enables configuration properties for email verification, auth tokens, cookies,
 * and rate limiting.
 */
@Configuration
@EnableConfigurationProperties({
        EmailVerificationProperties.class,
        AuthTokenProperties.class,
        AuthCookieProperties.class,
        PasswordResetProperties.class,
        AuthRateLimitProperties.class
})
public class IdentityConfiguration {
}
