package com.milestoneflow.identity.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailNormalizationResult")
class EmailNormalizationResultTest {

    @Nested
    @DisplayName("normalize()")
    class Normalize {

        @Test
        @DisplayName("trims leading and trailing whitespace")
        void trimsWhitespace() {
            var result = EmailNormalizationResult.normalize("  user@example.com  ");
            assertThat(result.displayEmail()).isEqualTo("user@example.com");
            assertThat(result.normalizedEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("applies NFC normalization")
        void appliesNfc() {
            // é can be composed (U+00E9) or decomposed (U+0065 + U+0301)
            String composed = "usér@example.com";
            String decomposed = "usér@example.com";
            var r1 = EmailNormalizationResult.normalize(composed);
            var r2 = EmailNormalizationResult.normalize(decomposed);
            assertThat(r1.normalizedEmail()).isEqualTo(r2.normalizedEmail());
        }

        @Test
        @DisplayName("lowercases with Locale.ROOT")
        void lowercasesWithRootLocale() {
            var result = EmailNormalizationResult.normalize("User@Example.COM");
            assertThat(result.normalizedEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("displayEmail preserves original case after trim")
        void displayEmailPreservesCase() {
            var result = EmailNormalizationResult.normalize("  User@Example.COM  ");
            assertThat(result.displayEmail()).isEqualTo("User@Example.COM");
        }

        @Test
        @DisplayName("does not remove Gmail dots")
        void doesNotRemoveGmailDots() {
            var result = EmailNormalizationResult.normalize("u.s.e.r@gmail.com");
            assertThat(result.normalizedEmail()).isEqualTo("u.s.e.r@gmail.com");
        }

        @Test
        @DisplayName("does not remove plus alias")
        void doesNotRemovePlusAlias() {
            var result = EmailNormalizationResult.normalize("user+alias@example.com");
            assertThat(result.normalizedEmail()).isEqualTo("user+alias@example.com");
        }

        @Test
        @DisplayName("unicode equivalent inputs produce same normalized")
        void unicodeEquivalentSameNormalized() {
            // Turkish İ (U+0130) vs ASCII I — they are different in NFC, same when lowercased
            // Use a simpler case: Greek sigma
            var r1 = EmailNormalizationResult.normalize("ΣIGMA@example.com");
            var r2 = EmailNormalizationResult.normalize("σigma@example.com");
            // NFC lowercased should produce the same result
            assertThat(r1.normalizedEmail()).isEqualTo(r2.normalizedEmail());
        }

        @Test
        @DisplayName("rejects null input")
        void rejectsNull() {
            assertThatThrownBy(() -> EmailNormalizationResult.normalize(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("rawEmail");
        }

        @Test
        @DisplayName("rejects blank input")
        void rejectsBlank() {
            assertThatThrownBy(() -> EmailNormalizationResult.normalize("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("rejects empty input")
        void rejectsEmpty() {
            assertThatThrownBy(() -> EmailNormalizationResult.normalize(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }
    }
}
