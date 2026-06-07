package com.milestoneflow.identity.domain.type;

/**
 * Lifecycle status of an {@link com.milestoneflow.identity.domain.model.AuthSession}.
 *
 * <p>Enum names must match the database CHECK constraint exactly:
 * {@code status IN ('ACTIVE', 'REVOKED', 'EXPIRED')}.
 *
 * <p>State transitions per B1 Authentication Baseline:
 * <pre>
 *   ACTIVE ──revoke──→ REVOKED
 *   ACTIVE ──expire──→ EXPIRED
 * </pre>
 *
 * <p>A REVOKED or EXPIRED session must never transition back to ACTIVE.
 */
public enum AuthSessionStatus {

    ACTIVE,
    REVOKED,
    EXPIRED
}
