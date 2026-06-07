package com.milestoneflow.identity.domain.policy;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalizes email addresses for storage and uniqueness lookup.
 *
 * <p>Produces two outputs:
 * <ul>
 *   <li>{@code displayEmail} — trimmed, NFC-normalized, preserves original case.</li>
 *   <li>{@code normalizedEmail} — trimmed, NFC-normalized, lowercased with {@link Locale#ROOT}.</li>
 * </ul>
 *
 * <p>Per B1 Authentication Baseline:
 * <ul>
 *   <li>No Gmail-specific dot removal.</li>
 *   <li>No plus-alias removal.</li>
 *   <li>No provider-specific rules.</li>
 *   <li>Uses {@link Locale#ROOT} for lowercasing (not default locale).</li>
 * </ul>
 *
 * @param displayEmail    trimmed + NFC, preserves case
 * @param normalizedEmail trimmed + NFC + lowercase(ROOT)
 */
public record EmailNormalizationResult(String displayEmail, String normalizedEmail) {

    /**
     * Normalizes the given raw email into display and normalized forms.
     *
     * @param rawEmail the raw email input from the user
     * @return normalization result
     * @throws NullPointerException     if rawEmail is null
     * @throws IllegalArgumentException if rawEmail is blank after trimming
     */
    public static EmailNormalizationResult normalize(String rawEmail) {
        Objects.requireNonNull(rawEmail, "rawEmail must not be null");

        String trimmed = rawEmail.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        String nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        String displayEmail = nfc;
        String normalizedEmail = nfc.toLowerCase(Locale.ROOT);

        return new EmailNormalizationResult(displayEmail, normalizedEmail);
    }

    /**
     * Returns the raw byte length of the display email in UTF-8.
     * Useful for validating against database column constraints.
     */
    public int displayEmailByteLength() {
        return displayEmail.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Returns the raw byte length of the normalized email in UTF-8.
     */
    public int normalizedEmailByteLength() {
        return normalizedEmail.getBytes(StandardCharsets.UTF_8).length;
    }
}
