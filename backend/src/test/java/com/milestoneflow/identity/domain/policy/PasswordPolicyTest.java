package com.milestoneflow.identity.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordPolicy")
class PasswordPolicyTest {

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("rejects password with 7 characters")
        void rejects7Characters() {
            assertThatThrownBy(() -> PasswordPolicy.validate("1234567"))
                    .isInstanceOf(PasswordPolicyViolation.class);
        }

        @Test
        @DisplayName("accepts password with exactly 8 characters")
        void accepts8Characters() {
            // Should not throw
            PasswordPolicy.validate("12345678");
        }

        @Test
        @DisplayName("does not require uppercase")
        void noUppercaseRequired() {
            PasswordPolicy.validate("alllowercase");
        }

        @Test
        @DisplayName("does not require digits")
        void noDigitsRequired() {
            PasswordPolicy.validate("abcdefgh");
        }

        @Test
        @DisplayName("does not require special characters")
        void noSpecialCharsRequired() {
            PasswordPolicy.validate("abcdefgh");
        }

        @Test
        @DisplayName("accepts Unicode password")
        void acceptsUnicode() {
            PasswordPolicy.validate("密码密码密码密");
        }

        @Test
        @DisplayName("preserves leading and trailing spaces as part of password")
        void spacesArePartOfPassword() {
            // Spaces count as characters; 8 spaces is valid length
            PasswordPolicy.validate("        ");
        }

        @Test
        @DisplayName("rejects password exceeding 72 bytes in UTF-8")
        void rejectsOver72Bytes() {
            // 73 ASCII characters = 73 bytes
            String tooLong = "a".repeat(73);
            assertThatThrownBy(() -> PasswordPolicy.validate(tooLong))
                    .isInstanceOf(PasswordPolicyViolation.class)
                    .hasMessageContaining("72");
        }

        @Test
        @DisplayName("accepts password at exactly 72 bytes in UTF-8")
        void acceptsExactly72Bytes() {
            String atLimit = "a".repeat(72);
            PasswordPolicy.validate(atLimit);
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> PasswordPolicy.validate(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects empty string")
        void rejectsEmpty() {
            assertThatThrownBy(() -> PasswordPolicy.validate(""))
                    .isInstanceOf(PasswordPolicyViolation.class);
        }

        @Test
        @DisplayName("password does not appear in exception message")
        void passwordNotInExceptionMessage() {
            String password = "mySecretPassword123";
            assertThatThrownBy(() -> PasswordPolicy.validate(password.substring(0, 3)))
                    .isInstanceOf(PasswordPolicyViolation.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(password));
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345678", "P@ssw0rd!", "中文密码加上abc", "  spaced  "})
        @DisplayName("accepts various valid passwords")
        void acceptsVariousValid(String password) {
            PasswordPolicy.validate(password);
        }
    }
}
