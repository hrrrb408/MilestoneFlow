package com.milestoneflow.workspace.domain.model;

import com.milestoneflow.shared.persistence.AuditedEntity;
import com.milestoneflow.workspace.domain.type.WorkspaceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Workspace entity — the tenant boundary for MilestoneFlow.
 *
 * <p>Mapped to {@code workspace} table (V003). Each workspace represents
 * an independent tenant with its own currency, timezone, and settings.
 *
 * <p>Per ADR-BE-001, the domain model directly serves as the JPA entity.
 * Per ADR-BE-006, foreign keys are stored as UUID IDs — no JPA {@code @ManyToOne}
 * relationships to other modules.
 *
 * <h3>Sensitive data</h3>
 * <p>{@code settings} JSONB is excluded from {@link #toString()} to prevent
 * accidental logging of potentially sensitive configuration data.
 *
 * @see com.milestoneflow.shared.persistence.AuditedEntity
 */
@Entity
@Table(name = "workspace")
public class Workspace extends AuditedEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 80, unique = true)
    private String slug;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private WorkspaceStatus status;

    @Column(name = "settings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> settings;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * No-arg constructor for JPA proxy instantiation.
     */
    protected Workspace() {
    }

    private Workspace(UUID id, String name, String slug, String defaultCurrency,
                      String timezone, WorkspaceStatus status) {
        super(id);
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.slug = Objects.requireNonNull(slug, "slug must not be null");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency must not be null");
        this.timezone = Objects.requireNonNull(timezone, "timezone must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Creates a new workspace in ACTIVE state.
     *
     * <p>The caller is responsible for:
     * <ul>
     *   <li>Generating the ID via {@link com.milestoneflow.shared.id.IdGenerator}.</li>
     *   <li>Normalizing and validating the slug.</li>
     *   <li>Validating the currency format (3 uppercase letters).</li>
     * </ul>
     *
     * @param id              client-generated UUID v7
     * @param name            workspace display name
     * @param slug            unique URL-friendly identifier
     * @param defaultCurrency 3-letter uppercase currency code (e.g., "TWD")
     * @param timezone        IANA timezone ID (e.g., "Asia/Taipei")
     * @return a new transient Workspace entity
     */
    public static Workspace create(UUID id, String name, String slug,
                                   String defaultCurrency, String timezone) {
        return new Workspace(id, name, slug, defaultCurrency, timezone, WorkspaceStatus.ACTIVE);
    }

    // ── Domain behaviour ─────────────────────────────────────────────────

    /**
     * Updates basic workspace information.
     *
     * <p>Only non-null parameters are applied; null parameters are ignored.
     * V0.1 does not allow slug changes.
     *
     * @param name            new name (nullable to skip)
     * @param timezone        new timezone (nullable to skip)
     * @param defaultCurrency new currency (nullable to skip)
     */
    public void updateBasicInfo(String name, String timezone, String defaultCurrency) {
        if (name != null) {
            this.name = name;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (defaultCurrency != null) {
            this.defaultCurrency = defaultCurrency;
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getTimezone() {
        return timezone;
    }

    public WorkspaceStatus getStatus() {
        return status;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public UUID getArchivedBy() {
        return archivedBy;
    }

    public long getVersion() {
        return version;
    }

    // ── toString (excludes settings) ─────────────────────────────────────

    @Override
    public String toString() {
        return "Workspace{id=" + getId()
                + ", name='" + name + "'"
                + ", slug='" + slug + "'"
                + ", status=" + status
                + ", defaultCurrency='" + defaultCurrency + "'"
                + ", timezone='" + timezone + "'"
                + ", version=" + version
                + '}';
    }
}
