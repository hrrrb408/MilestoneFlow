package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.application.port.out.PasswordResetEmailSender;
import com.milestoneflow.identity.application.port.out.VerificationEmailSender;
import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
import com.milestoneflow.identity.infrastructure.config.PasswordResetProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Email infrastructure configuration.
 *
 * <p>Registers the correct {@link VerificationEmailSender} and
 * {@link PasswordResetEmailSender} beans based on the active profile
 * and the {@code milestoneflow.mail.provider} property.
 *
 * <h3>Profile / Provider matrix:</h3>
 * <table>
 *   <tr><th>Profile</th><th>provider</th><th>Result</th></tr>
 *   <tr><td>local/test</td><td>noop (default)</td><td>Noop senders</td></tr>
 *   <tr><td>prod</td><td>smtp (required)</td><td>SMTP senders</td></tr>
 *   <tr><td>prod</td><td>noop / missing</td><td>Startup failure (fail-closed)</td></tr>
 * </table>
 *
 * <p>Noop senders are defined in {@link NoopVerificationEmailSender} and
 * {@link NoopPasswordResetEmailSender} with {@code @Profile({"local","test"})} guards.
 * SMTP senders are defined here with {@code @Profile("prod")} and
 * {@code @ConditionalOnProperty(provider=smtp)}.
 *
 * <p>When running in production with no matching sender bean, Spring's dependency
 * injection will fail at startup (fail-closed), preventing registration and
 * password reset flows from silently doing nothing.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

    /**
     * SMTP verification email sender — active only in production with provider=smtp.
     *
     * <p>Uses Spring's {@link JavaMailSender} for actual SMTP delivery.
     * Raw token is used only to construct the verification link inside the email body.
     */
    @Bean
    @Profile("prod")
    @ConditionalOnProperty(name = "milestoneflow.mail.provider", havingValue = "smtp")
    VerificationEmailSender smtpVerificationEmailSender(
            JavaMailSender mailSender,
            MailProperties mailProperties,
            EmailVerificationProperties emailVerificationProperties
    ) {
        return new SmtpVerificationEmailSender(
                mailSender, mailProperties, emailVerificationProperties);
    }

    /**
     * SMTP password reset email sender — active only in production with provider=smtp.
     *
     * <p>Uses Spring's {@link JavaMailSender} for actual SMTP delivery.
     * Raw token is used only to construct the reset link inside the email body.
     */
    @Bean
    @Profile("prod")
    @ConditionalOnProperty(name = "milestoneflow.mail.provider", havingValue = "smtp")
    PasswordResetEmailSender smtpPasswordResetEmailSender(
            JavaMailSender mailSender,
            MailProperties mailProperties,
            PasswordResetProperties passwordResetProperties
    ) {
        return new SmtpPasswordResetEmailSender(
                mailSender, mailProperties, passwordResetProperties);
    }
}
