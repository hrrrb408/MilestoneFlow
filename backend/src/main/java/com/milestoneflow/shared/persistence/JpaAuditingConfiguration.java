package com.milestoneflow.shared.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Auditing configuration.
 *
 * <p>Enables Spring Data JPA auditing infrastructure for {@code @CreatedDate},
 * {@code @LastModifiedDate}, {@code @CreatedBy}, and {@code @LastModifiedBy}
 * annotations on entity fields.
 *
 * <p>Uses the application-wide UTC {@link Clock} bean for timestamp generation,
 * ensuring deterministic time in tests via {@link Clock#fixed}.
 *
 * <p>The {@link AuditorAware} returns {@link Optional#empty()} because the
 * authentication context is not yet established (no Spring Security in this
 * milestone). When authentication is implemented, this bean will be replaced
 * with a real implementation that extracts the actor from the security context.
 *
 * <p>Per ADR-BE-005:
 * <ul>
 *   <li>JPA auditing is NOT the same as {@code audit_event} (append-only log).</li>
 *   <li>{@code created_by}/{@code updated_by} are allowed to be null.</li>
 *   <li>No fake system user is created.</li>
 * </ul>
 *
 * @see com.milestoneflow.shared.time.TimeConfiguration
 */
@Configuration
@EnableJpaAuditing(
        auditorAwareRef = "auditorAware",
        dateTimeProviderRef = "auditingDateTimeProvider"
)
public class JpaAuditingConfiguration {

    @Bean
    AuditorAware<UUID> auditorAware() {
        // Authentication context not yet established.
        // Returns empty so that created_by/updated_by remain null.
        // To be replaced when Spring Security is integrated.
        return () -> Optional.empty();
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
