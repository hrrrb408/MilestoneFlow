package com.milestoneflow.workspace.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Spring Data interface-based projection for the workspace member roster
 * read query (per ADR-BE-007, Option A interface projection).
 *
 * <p>Backed by a native-SQL join across {@code workspace_membership} and
 * {@code app_user}. It selects only the safe display fields and never exposes
 * {@code password_hash}, {@code email_normalized}, or token material.
 *
 * <p>{@code joinedAt} is read as {@link OffsetDateTime} — the deterministic
 * JDBC-native type for a Postgres {@code timestamptz} column — and converted
 * to {@link java.time.Instant} in the adapter. This avoids relying on Spring
 * Data's automatic {@code timestamptz → Instant} projection conversion.
 *
 * <p>Package-private per ARCH-005/ARCH-010: this infrastructure query type is
 * not leaked to the application layer. The adapter maps it to the application
 * {@link com.milestoneflow.workspace.application.result.WorkspaceMemberResult}.
 */
interface WorkspaceMemberProjection {

    UUID getUserId();

    String getEmail();

    String getDisplayName();

    String getRole();

    String getStatus();

    OffsetDateTime getJoinedAt();
}
