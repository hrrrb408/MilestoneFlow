package com.milestoneflow.workspace.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface-based projection for the workspace member roster
 * read query (per ADR-BE-007, Option A interface projection).
 *
 * <p>Backed by a native-SQL join across {@code workspace_membership} and
 * {@code app_user}. It selects only the safe display fields and never exposes
 * {@code password_hash}, {@code email_normalized}, or token material.
 *
 * <p>{@code joinedAt} is read as {@link Instant} because Hibernate's native-query
 * result handling already maps a Postgres {@code timestamptz} column to
 * {@code Instant}; declaring any other java.time type (e.g. {@code OffsetDateTime})
 * triggers Spring Data's projection converter, which has no matching converter
 * and fails at runtime.
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

    Instant getJoinedAt();
}
