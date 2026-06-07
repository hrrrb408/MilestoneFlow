package com.milestoneflow.identity.domain.type;

/**
 * Lifecycle status of an {@link com.milestoneflow.identity.domain.model.AppUser}.
 *
 * <p>Enum names must match the database CHECK constraint exactly:
 * {@code status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')}.
 *
 * <p>State transitions per B1 Authentication Baseline:
 * <pre>
 *   PENDING_VERIFICATION ──verifyEmail──→ ACTIVE
 *   PENDING_VERIFICATION ──disable─────→ DISABLED
 *   ACTIVE ──disable───────────────────→ DISABLED
 *   DISABLED ──adminRestore────────────→ ACTIVE  (not in V0.1)
 * </pre>
 */
public enum UserStatus {

    PENDING_VERIFICATION,
    ACTIVE,
    DISABLED
}
