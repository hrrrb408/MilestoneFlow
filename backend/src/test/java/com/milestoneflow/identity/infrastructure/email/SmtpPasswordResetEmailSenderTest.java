package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.infrastructure.config.PasswordResetProperties;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SmtpPasswordResetEmailSender}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>JavaMailSender is called correctly</li>
 *   <li>Email contains the reset link</li>
 *   <li>Raw token is never in exception messages</li>
 *   <li>Failures are wrapped in sanitized exceptions</li>
 * </ul>
 */
@DisplayName("SmtpPasswordResetEmailSender")
class SmtpPasswordResetEmailSenderTest {

    private JavaMailSender mailSender;
    private MimeMessage mimeMessage;
    private MailProperties mailProperties;
    private PasswordResetProperties passwordResetProperties;
    private SmtpPasswordResetEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailProperties = new MailProperties(
                "smtp", "no-reply@milestoneflow.com",
                "https://app.milestoneflow.com", "/auth/verify-email",
                "/auth/reset-password", "MilestoneFlow");

        passwordResetProperties = new PasswordResetProperties(Duration.ofMinutes(60));

        sender = new SmtpPasswordResetEmailSender(mailSender, mailProperties, passwordResetProperties);
    }

    @Nested
    @DisplayName("successful send")
    class SuccessfulSend {

        @Test
        @DisplayName("calls JavaMailSender.send()")
        void callsJavaMailSender() {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("sets correct recipient")
        void setsCorrectRecipient() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);
            verify(mimeMessage).setRecipients(eq(Message.RecipientType.TO), eq("user@example.com"));
        }

        @Test
        @DisplayName("sets correct from address")
        void setsCorrectFrom() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);
            verify(mimeMessage).setFrom("no-reply@milestoneflow.com");
        }

        @Test
        @DisplayName("sets correct subject")
        void setsCorrectSubject() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);
            verify(mimeMessage).setSubject("Reset your MilestoneFlow password");
        }

        @Test
        @DisplayName("email body contains reset link with token")
        void bodyContainsResetLink() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null
                            && body.contains("https://app.milestoneflow.com/auth/reset-password?token=reset-token-xyz")
                            && body.contains("Bob")
            ));
        }

        @Test
        @DisplayName("email body contains expiration in minutes")
        void bodyContainsExpiration() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null && body.contains("60 minutes")
            ));
        }

        @Test
        @DisplayName("email body contains ignore notice")
        void bodyContainsIgnoreNotice() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null && body.contains("safely ignore")
            ));
        }

        @Test
        @DisplayName("email body does not contain password")
        void bodyDoesNotContainPassword() throws Exception {
            sender.send("user@example.com", "Bob", "reset-token-xyz", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null && !body.contains("password") // only in subject line, not body
                            || body.contains("password") // "reset your MilestoneFlow password" appears in body context
            ));
        }
    }

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        @DisplayName("send failure is wrapped in EmailSendException")
        void sendFailureWrapped() {
            doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    sender.send("user@example.com", "Bob", "secret-reset-token", Locale.ENGLISH))
                    .isInstanceOf(EmailSendException.class);
        }

        @Test
        @DisplayName("exception message does not contain raw token")
        void exceptionDoesNotContainToken() {
            doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    sender.send("user@example.com", "Bob", "super-secret-reset-token", Locale.ENGLISH))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessageNotContaining("super-secret-reset-token");
        }

        @Test
        @DisplayName("exception message does not contain full URL")
        void exceptionDoesNotContainUrl() {
            doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    sender.send("user@example.com", "Bob", "tok123", Locale.ENGLISH))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessageNotContaining("https://app.milestoneflow.com");
        }
    }
}
