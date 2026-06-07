package com.milestoneflow.identity.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Identity module configuration.
 *
 * <p>Enables configuration properties for email verification.
 */
@Configuration
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class IdentityConfiguration {
}
