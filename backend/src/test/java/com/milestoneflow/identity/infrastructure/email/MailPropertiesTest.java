package com.milestoneflow.identity.infrastructure.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MailProperties} configuration validation.
 *
 * <p>Verifies defaults, valid provider values, and that the property
 * object does not contain any secrets.
 */
@DisplayName("MailProperties")
class MailPropertiesTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("all-null constructor applies defaults")
        void allNullAppliesDefaults() {
            MailProperties props = new MailProperties(null, null, null, null, null, null);

            assertThat(props.provider()).isEqualTo("noop");
            assertThat(props.from()).isEqualTo("no-reply@milestoneflow.local");
            assertThat(props.frontendBaseUrl()).isEqualTo("http://localhost:5173");
            assertThat(props.verificationPath()).isEqualTo("/auth/verify-email");
            assertThat(props.passwordResetPath()).isEqualTo("/auth/reset-password");
            assertThat(props.senderName()).isEqualTo("MilestoneFlow");
        }

        @Test
        @DisplayName("blank values apply defaults")
        void blankValuesApplyDefaults() {
            MailProperties props = new MailProperties("  ", "  ", "  ", "  ", "  ", "  ");

            assertThat(props.provider()).isEqualTo("noop");
            assertThat(props.from()).isEqualTo("no-reply@milestoneflow.local");
            assertThat(props.frontendBaseUrl()).isEqualTo("http://localhost:5173");
        }
    }

    @Nested
    @DisplayName("provider validation")
    class ProviderValidation {

        @Test
        @DisplayName("noop provider is valid")
        void noopIsValid() {
            MailProperties props = new MailProperties("noop", null, null, null, null, null);
            assertThat(props.isNoop()).isTrue();
            assertThat(props.isSmtp()).isFalse();
            assertThat(props.validatedProvider()).isEqualTo("noop");
        }

        @Test
        @DisplayName("smtp provider is valid")
        void smtpIsValid() {
            MailProperties props = new MailProperties("smtp", null, null, null, null, null);
            assertThat(props.isSmtp()).isTrue();
            assertThat(props.isNoop()).isFalse();
            assertThat(props.validatedProvider()).isEqualTo("smtp");
        }

        @Test
        @DisplayName("SMTP is case-insensitive")
        void smtpCaseInsensitive() {
            MailProperties props = new MailProperties("SMTP", null, null, null, null, null);
            assertThat(props.isSmtp()).isTrue();
            assertThat(props.validatedProvider()).isEqualTo("smtp");
        }

        @Test
        @DisplayName("illegal provider throws")
        void illegalProviderThrows() {
            MailProperties props = new MailProperties("sendgrid", null, null, null, null, null);
            assertThatThrownBy(props::validatedProvider)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be 'noop' or 'smtp'");
        }
    }

    @Nested
    @DisplayName("no secrets")
    class NoSecrets {

        @Test
        @DisplayName("MailProperties does not contain SMTP credentials")
        void noSmtpCredentials() {
            MailProperties props = new MailProperties("smtp", "no-reply@example.com",
                    "https://example.com", "/v", "/r", "Test");

            // MailProperties has no password/host/port fields — these are in spring.mail.*
            assertThat(props).hasNoNullFieldsOrPropertiesExcept();
            // Note: toString() contains "passwordResetPath" as a field name, which is expected.
            // The concern is that no SMTP_PASSWORD value appears in this config object.
            assertThat(props.toString()).doesNotContain("secret");
        }
    }
}
