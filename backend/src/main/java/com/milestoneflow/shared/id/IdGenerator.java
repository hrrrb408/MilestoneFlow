package com.milestoneflow.shared.id;

import java.util.UUID;

/**
 * Stable abstraction for generating entity identifiers.
 *
 * <p>All business aggregates use this interface to obtain primary keys,
 * keeping the concrete UUID generation strategy isolated behind an adapter.
 * Production uses UUID v7; tests can inject a fixed implementation.
 *
 * <p>The returned {@link UUID} is intended for PostgreSQL {@code uuid} columns,
 * never stored as {@code varchar(36)}.
 *
 * @see UuidV7IdGenerator
 */
public interface IdGenerator {

    /**
     * Generate a new unique identifier.
     *
     * @return a non-null, globally unique UUID
     */
    UUID nextId();
}
