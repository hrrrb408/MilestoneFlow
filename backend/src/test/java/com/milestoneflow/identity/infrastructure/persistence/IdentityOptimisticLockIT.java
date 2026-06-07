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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying optimistic locking on {@link AppUser}.
 *
 * <p>Uses JPA for the initial save, then JDBC to simulate a concurrent
 * update that bypasses JPA's version check, and finally verifies that
 * JPA detects the stale version and throws
 * {@link ObjectOptimisticLockingFailureException}.
 */
@Transactional
class IdentityOptimisticLockIT extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = AppUser.create(UUID.randomUUID(), "lock-" + uniqueSuffix + "@example.test",
                "lock-" + uniqueSuffix + "@example.test", "Lock User", "a".repeat(60), "zh-TW");
        appUserRepository.save(user);
        entityManager.flush();
        entityManager.clear();
        userId = user.getId();
    }

    @Test
    @DisplayName("should throw OptimisticLockException on concurrent update")
    void shouldDetectConcurrentUpdate() {
        // Load user via JPA (managed entity, version=0)
        AppUser loaded = appUserRepository.findById(userId).orElseThrow();
        assertThat(loaded.getVersion()).isEqualTo(0);

        // Simulate concurrent update via raw JDBC (bypasses JPA version check)
        jdbc.update("UPDATE app_user SET version = 1, password_hash = ? WHERE id = ?",
                "b".repeat(60), userId);

        // Modify the stale JPA entity and try to flush
        loaded.changePasswordHash("c".repeat(60));
        assertThatThrownBy(() -> {
            appUserRepository.save(loaded);
            entityManager.flush();
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Verify: the JDBC update's change persisted, JPA's did not
        entityManager.clear();
        AppUser finalState = appUserRepository.findById(userId).orElseThrow();
        assertThat(finalState.getPasswordHash()).isEqualTo("b".repeat(60));
        assertThat(finalState.getVersion()).isEqualTo(1);
    }
}
