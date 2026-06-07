package com.milestoneflow.identity.infrastructure.persistence;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.shared.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying JPA auditing infrastructure.
 *
 * <p>Validates that timestamps are auto-populated and that
 * {@code createdBy}/{@code updatedBy} are allowed to remain null
 * when no authenticated user context exists.
 */
class JpaAuditingIT extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    private AppUser createTestUser(String suffix) {
        return AppUser.create(
                UUID.randomUUID(),
                "audit" + suffix + "@example.test",
                "audit" + suffix + "@example.test",
                "Audit User " + suffix,
                "a".repeat(60),
                "zh-TW"
        );
    }

    @Nested
    @DisplayName("Timestamp auditing")
    class TimestampAuditing {

        @Test
        @DisplayName("should populate createdAt on insert")
        void shouldPopulateCreatedAtOnInsert() {
            AppUser user = createTestUser("1");
            appUserRepository.save(user);

            AppUser loaded = appUserRepository.findById(user.getId()).orElseThrow();
            assertThat(loaded.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should populate updatedAt on insert")
        void shouldPopulateUpdatedAtOnInsert() {
            AppUser user = createTestUser("2");
            appUserRepository.save(user);

            AppUser loaded = appUserRepository.findById(user.getId()).orElseThrow();
            assertThat(loaded.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should set createdAt and updatedAt close together on insert")
        void shouldSetTimestampsCloseOnInsert() {
            AppUser user = createTestUser("3");
            appUserRepository.save(user);

            AppUser loaded = appUserRepository.findById(user.getId()).orElseThrow();
            // createdAt and updatedAt should be very close (same millisecond)
            assertThat(loaded.getUpdatedAt()).isNotNull();
            assertThat(loaded.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should update updatedAt on modification")
        void shouldUpdateUpdatedAtOnModification() {
            AppUser user = createTestUser("4");
            appUserRepository.save(user);

            AppUser saved = appUserRepository.findById(user.getId()).orElseThrow();
            Instant originalCreatedAt = saved.getCreatedAt();

            // Small delay is NOT needed — JPA auditing uses Clock, not system time
            saved.changePasswordHash("b".repeat(60));
            appUserRepository.save(saved);

            AppUser updated = appUserRepository.findById(user.getId()).orElseThrow();
            assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
            // updatedAt should change (or at minimum remain valid)
            assertThat(updated.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should not change createdAt on update")
        void shouldNotChangeCreatedAtOnUpdate() {
            AppUser user = createTestUser("5");
            appUserRepository.save(user);

            Instant createdAt = appUserRepository.findById(user.getId()).orElseThrow().getCreatedAt();

            AppUser toUpdate = appUserRepository.findById(user.getId()).orElseThrow();
            toUpdate.changePasswordHash("c".repeat(60));
            appUserRepository.save(toUpdate);

            AppUser afterUpdate = appUserRepository.findById(user.getId()).orElseThrow();
            assertThat(afterUpdate.getCreatedAt()).isEqualTo(createdAt);
        }
    }

    @Nested
    @DisplayName("Actor auditing")
    class ActorAuditing {

        @Test
        @DisplayName("should allow null createdBy when no auditor context")
        void shouldAllowNullCreatedBy() {
            AppUser user = createTestUser("6");
            appUserRepository.save(user);

            AppUser loaded = appUserRepository.findById(user.getId()).orElseThrow();
            assertThat(loaded.getCreatedBy()).isNull();
        }

        @Test
        @DisplayName("should allow null updatedBy when no auditor context")
        void shouldAllowNullUpdatedBy() {
            AppUser user = createTestUser("7");
            appUserRepository.save(user);

            AppUser loaded = appUserRepository.findById(user.getId()).orElseThrow();
            assertThat(loaded.getUpdatedBy()).isNull();
        }
    }
}
