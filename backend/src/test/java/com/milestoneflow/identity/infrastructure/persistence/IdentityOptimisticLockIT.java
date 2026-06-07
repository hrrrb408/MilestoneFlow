package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying optimistic locking on {@link AppUser}.
 *
 * <p>Uses two independent {@link EntityManager} instances to simulate
 * concurrent transactions competing for the same row.
 */
class IdentityOptimisticLockIT extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        AppUser user = AppUser.create(UUID.randomUUID(), "lock@example.test",
                "lock@example.test", "Lock User", "a".repeat(60), "zh-TW");
        appUserRepository.save(user);
        userId = user.getId();
    }

    @Test
    @DisplayName("should throw OptimisticLockException on concurrent update")
    void shouldDetectConcurrentUpdate() {
        // Transaction 1: load and modify
        AppUser user1 = appUserRepository.findById(userId).orElseThrow();
        user1.activateAfterEmailVerification(Instant.parse("2026-06-01T12:00:00Z"));

        // Transaction 2: load same user via direct EM (bypasses repository cache)
        entityManager.clear(); // detach all managed entities
        AppUser user2 = entityManager.find(AppUser.class, userId);
        assertThat(user2).isNotNull();
        user2.changePasswordHash("b".repeat(60));
        entityManager.persist(user2);
        entityManager.flush();
        long versionAfterTx2 = user2.getVersion();

        // Transaction 1: now try to flush — should fail
        assertThatThrownBy(() -> {
            entityManager.persist(user1);
            entityManager.flush();
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Verify: only transaction 2's change persisted
        entityManager.clear();
        AppUser finalState = entityManager.find(AppUser.class, userId);
        assertThat(finalState).isNotNull();
        assertThat(finalState.getPasswordHash()).isEqualTo("b".repeat(60));
        assertThat(finalState.getEmailVerifiedAt()).isNull(); // tx1 change lost
        assertThat(finalState.getVersion()).isEqualTo(versionAfterTx2);
    }
}
