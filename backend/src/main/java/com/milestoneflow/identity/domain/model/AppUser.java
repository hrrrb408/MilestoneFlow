package com.milestoneflow.identity.domain.model;

import com.milestoneflow.identity.domain.type.UserStatus;
import com.milestoneflow.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Global login identity for a MilestoneFlow user.
 *
 * <p>Mapped to {@code app_user} table (V002). Workspace roles are expressed
 * via {@code workspace_membership}, not stored here.
 *
 * <p>Per ADR-BE-001, the domain model directly serves as the JPA entity.
 * Per ADR-BE-006, foreign keys are stored as UUID IDs — no JPA {@code @ManyToOne}
 * relationships to other modules.
 *
 * <h3>Sensitive data</h3>
 * <p>{@code passwordHash} is excluded from {@link #toString()} to prevent
 * accidental logging of credential material.
 *
 * @see com.milestoneflow.shared.persistence.AuditedEntity
 */
@Entity
@Table(name = "app_user")
public class AppUser extends AuditedEntity {

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 320, unique = true)
    private String emailNormalized;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected AppUser() {
    }

    private AppUser(UUID id, String email, String emailNormalized,
                    String displayName, String passwordHash, String locale) {
        super(id);
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.emailNormalized = Objects.requireNonNull(emailNormalized, "emailNormalized must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
        this.status = UserStatus.PENDING_VERIFICATION;
    }

    /**
     * Creates a new user in {@code PENDING_VERIFICATION} state.
     *
     * <p>The caller is responsible for:
     * <ul>
     *   <li>Generating the ID via {@link com.milestoneflow.shared.id.IdGenerator}.</li>
     *   <li>Normalizing the email before passing it as {@code emailNormalized}.</li>
     *   <li>Encoding the password before passing it as {@code passwordHash}.</li>
     * </ul>
     *
     * @param id               client-generated UUID v7
     * @param email            raw email address
     * @param emailNormalized  normalized email for uniqueness lookup
     * @param displayName      human-readable name
     * @param passwordHash     encoded password hash
     * @param locale           user locale (e.g., "zh-TW")
     * @return a new transient AppUser entity
     */
    public static AppUser create(UUID id, String email, String emailNormalized,
                                 String displayName, String passwordHash, String locale) {
        return new AppUser(id, email, emailNormalized, displayName, passwordHash, locale);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Activates this user after successful email verification.
     *
     * <p>Valid transition: {@code PENDING_VERIFICATION → ACTIVE}.
     * <p>If the user is already {@code ACTIVE}, this is a no-op (idempotent).
     *
     * @param verifiedAt the instant when email was verified
     * @throws IllegalStateException if the user is {@code DISABLED}
     */
    public void activateAfterEmailVerification(Instant verifiedAt) {
        if (status == UserStatus.DISABLED) {
            throw new IllegalStateException(
                    "Cannot activate a DISABLED user via email verification");
        }
        if (status == UserStatus.ACTIVE) {
            return; // idempotent
        }
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    }

    /**
     * Disables this user. Valid transitions: {@code PENDING_VERIFICATION → DISABLED},
     * {@code ACTIVE → DISABLED}.
     *
     * <p>Session revocation is handled by the application service, not by this method.
     *
     * @throws IllegalStateException if already {@code DISABLED}
     */
    public void disable() {
        if (status == UserStatus.DISABLED) {
            throw new IllegalStateException("User is already DISABLED");
        }
        this.status = UserStatus.DISABLED;
    }

    /**
     * Records the time of a successful login.
     *
     * @param loginAt the instant of login
     */
    public void recordSuccessfulLogin(Instant loginAt) {
        this.lastLoginAt = Objects.requireNonNull(loginAt, "loginAt must not be null");
    }

    /**
     * Replaces the password hash. The caller is responsible for encoding.
     *
     * @param newPasswordHash the new encoded password hash
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash must not be null");
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the encoded password hash.
     * Excluded from {@link #toString()} to prevent credential leakage in logs.
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getLocale() {
        return locale;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public long getVersion() {
        return version;
    }

    // ── toString (excludes passwordHash) ──────────────────────────────────

    @Override
    public String toString() {
        return "AppUser{id=" + getId()
                + ", email='" + email + "'"
                + ", emailNormalized='" + emailNormalized + "'"
                + ", displayName='" + displayName + "'"
                + ", status=" + status
                + ", locale='" + locale + "'"
                + ", emailVerifiedAt=" + emailVerifiedAt
                + ", lastLoginAt=" + lastLoginAt
                + ", version=" + version
                + '}';
    }
}
