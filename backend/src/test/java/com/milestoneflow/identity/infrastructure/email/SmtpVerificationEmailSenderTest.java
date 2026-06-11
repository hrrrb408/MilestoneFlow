package com.milestoneflow.identity.infrastructure.email;

import com.milestoneflow.identity.infrastructure.config.EmailVerificationProperties;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SmtpVerificationEmailSender}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>JavaMailSender is called correctly</li>
 *   <li>Email contains the verification link</li>
 *   <li>Raw token is never logged (verified via log appender would be ideal;
 *       here we verify the code path does not expose the token in exceptions)</li>
 *   <li>Failures are wrapped in sanitized exceptions</li>
 * </ul>
 */
@DisplayName("SmtpVerificationEmailSender")
class SmtpVerificationEmailSenderTest {

    private JavaMailSender mailSender;
    private MimeMessage mimeMessage;
    private MailProperties mailProperties;
    private EmailVerificationProperties verificationProperties;
    private SmtpVerificationEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailProperties = new MailProperties(
                "smtp", "no-reply@milestoneflow.com",
                "https://app.milestoneflow.com", "/auth/verify-email",
                "/auth/reset-password", "MilestoneFlow");

        verificationProperties = new EmailVerificationProperties(Duration.ofHours(24));

        sender = new SmtpVerificationEmailSender(mailSender, mailProperties, verificationProperties);
    }

    @Nested
    @DisplayName("successful send")
    class SuccessfulSend {

        @Test
        @DisplayName("calls JavaMailSender.send()")
        void callsJavaMailSender() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("sets correct recipient")
        void setsCorrectRecipient() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mimeMessage).setRecipients(eq(Message.RecipientType.TO), eq("user@example.com"));
        }

        @Test
        @DisplayName("sets correct from address")
        void setsCorrectFrom() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mimeMessage).setFrom("no-reply@milestoneflow.com");
        }

        @Test
        @DisplayName("sets correct subject")
        void setsCorrectSubject() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mimeMessage).setSubject("Verify your MilestoneFlow email");
        }

        @Test
        @DisplayName("email body contains verification link with token")
        void bodyContainsVerificationLink() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null
                            && body.contains("https://app.milestoneflow.com/auth/verify-email?token=raw-token-abc")
                            && body.contains("24 hours")
                            && body.contains("Alice")
            ));
        }

        @Test
        @DisplayName("email body contains expiration notice")
        void bodyContainsExpiration() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null && body.contains("expire in 24 hours")
            ));
        }

        @Test
        @DisplayName("email body contains ignore notice")
        void bodyContainsIgnoreNotice() throws Exception {
            sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH);

            verify(mimeMessage).setText(argThat(body ->
                    body != null && body.contains("safely ignore")
            ));
        }
    }

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        @DisplayName("MessagingException is wrapped in EmailSendException")
        void messagingExceptionWrapped() {
            doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    sender.send("user@example.com", "Alice", "raw-token-abc", Locale.ENGLISH))
                    .isInstanceOf(EmailSendException.class);
        }

        @Test
        @DisplayName("exception message does not contain raw token")
        void exceptionDoesNotContainToken() {
            doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    sender.send("user@example.com", "Alice", "super-secret-token-xyz", Locale.ENGLISH))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessageNotContaining("super-secret-token-xyz");
        }

        @Test
        @DisplayName("exception message does not contain full URL")
        void exceptionDoesNotContainUrl() {
            doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    sender.send("user@example.com", "Alice", "tok123", Locale.ENGLISH))
                    .isInstanceOf(EmailSendException.class)
                    .hasMessageNotContaining("https://app.milestoneflow.com");
        }
    }
}
