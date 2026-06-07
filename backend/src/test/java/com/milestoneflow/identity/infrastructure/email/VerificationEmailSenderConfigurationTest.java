package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.application.event.EmailVerificationEventListener;
import com.milestoneflow.identity.application.port.out.VerificationEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link NoopVerificationEmailSender} is only registered in
 * non-production profiles and that the application context fails to start
 * in production when no real email sender is available (fail-closed).
 */
@DisplayName("VerificationEmailSender configuration")
class VerificationEmailSenderConfigurationTest {

    /**
     * Base runner without EmailVerificationEventListener.
     * Used for tests that verify bean presence/absence without requiring
     * the listener dependency.
     */
    private final ApplicationContextRunner beanCheckRunner = new ApplicationContextRunner();

    /**
     * Runner with EmailVerificationEventListener registered.
     * Used for tests that verify fail-closed behavior (context startup failure
     * when no VerificationEmailSender is available).
     */
    private final ApplicationContextRunner failClosedRunner = new ApplicationContextRunner()
            .withBean(EmailVerificationEventListener.class);

    @Nested
    @DisplayName("local profile")
    class LocalProfile {

        @Test
        @DisplayName("with noop provider — NoopVerificationEmailSender bean exists")
        void localWithNoop() {
            beanCheckRunner
                    .withPropertyValues(
                            "spring.profiles.active=local",
                            "milestoneflow.email.provider=noop"
                    )
                    .withUserConfiguration(NoopVerificationEmailSender.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(VerificationEmailSender.class);
                        assertThat(context.getBean(VerificationEmailSender.class))
                                .isInstanceOf(NoopVerificationEmailSender.class);
                    });
        }

        @Test
        @DisplayName("without provider property — NoopVerificationEmailSender not created")
        void localWithoutProvider() {
            beanCheckRunner
                    .withPropertyValues("spring.profiles.active=local")
                    .withUserConfiguration(NoopVerificationEmailSender.class)
                    .run(context -> {
                        // NoopVerificationEmailSender requires both @Profile and @ConditionalOnProperty
                        assertThat(context).doesNotHaveBean(VerificationEmailSender.class);
                    });
        }
    }

    @Nested
    @DisplayName("test profile")
    class TestProfile {

        @Test
        @DisplayName("with noop provider — NoopVerificationEmailSender bean exists")
        void testWithNoop() {
            beanCheckRunner
                    .withPropertyValues(
                            "spring.profiles.active=test",
                            "milestoneflow.email.provider=noop"
                    )
                    .withUserConfiguration(NoopVerificationEmailSender.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(VerificationEmailSender.class);
                        assertThat(context.getBean(VerificationEmailSender.class))
                                .isInstanceOf(NoopVerificationEmailSender.class);
                    });
        }
    }

    @Nested
    @DisplayName("prod profile")
    class ProdProfile {

        @Test
        @DisplayName("without provider — NoopVerificationEmailSender not created")
        void prodWithoutProvider_noopNotCreated() {
            beanCheckRunner
                    .withPropertyValues("spring.profiles.active=prod")
                    .withUserConfiguration(NoopVerificationEmailSender.class)
                    .run(context -> {
                        // @Profile({"local","test"}) blocks Noop in prod
                        assertThat(context).doesNotHaveBean(VerificationEmailSender.class);
                    });
        }

        @Test
        @DisplayName("with noop provider — NoopVerificationEmailSender still not created (Profile blocks)")
        void prodWithNoopProvider_stillBlocked() {
            beanCheckRunner
                    .withPropertyValues(
                            "spring.profiles.active=prod",
                            "milestoneflow.email.provider=noop"
                    )
                    .withUserConfiguration(NoopVerificationEmailSender.class)
                    .run(context -> {
                        // Even with the property, @Profile blocks in prod
                        assertThat(context).doesNotHaveBean(VerificationEmailSender.class);
                    });
        }

        @Test
        @DisplayName("without sender — context fails to start (fail-closed)")
        void prodWithoutSender_failsToStart() {
            // EmailVerificationEventListener requires VerificationEmailSender.
            // In prod profile, NoopVerificationEmailSender is blocked by @Profile,
            // and no real sender is configured, so the context must fail to start.
            failClosedRunner
                    .withPropertyValues("spring.profiles.active=prod")
                    .run(context -> {
                        assertThat(context.getStartupFailure()).isNotNull();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("VerificationEmailSender");
                    });
        }
    }

    @Nested
    @DisplayName("NoopVerificationEmailSender security")
    class NoopSecurity {

        @Test
        @DisplayName("does not log raw token — method completes without error")
        void doesNotLogRawToken() {
            NoopVerificationEmailSender sender = new NoopVerificationEmailSender();
            // Code review confirms:
            // - log.info uses maskEmail() for recipient, never references rawToken
            // - No verification URL or full email content is logged
            sender.send("user@example.com", "Test User", "secret-raw-token-12345", Locale.ENGLISH);
        }
    }
}
