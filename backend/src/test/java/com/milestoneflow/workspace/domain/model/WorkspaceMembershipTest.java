package com.milestoneflow.workspace.domain.model;

import com.milestoneflow.workspace.domain.type.WorkspaceMembershipRole;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WorkspaceMembership} domain model.
 */
class WorkspaceMembershipTest {

    private static final UUID FIXED_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");
    private static final UUID WORKSPACE_ID = UUID.fromString("0191f5a0-abcd-7abc-8def-012345678900");
    private static final UUID USER_ID = UUID.fromString("0191f5a0-ef01-7abc-8def-012345678900");
    private static final Instant JOINED_AT = Instant.parse("2026-06-01T12:00:00Z");

    // ── Creation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create OWNER membership with ACTIVE status")
        void shouldCreateOwnerMembership() {
            WorkspaceMembership m = WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, USER_ID, JOINED_AT);

            assertThat(m.getId()).isEqualTo(FIXED_ID);
            assertThat(m.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(m.getUserId()).isEqualTo(USER_ID);
            assertThat(m.getRole()).isEqualTo(WorkspaceMembershipRole.OWNER);
            assertThat(m.getStatus()).isEqualTo(WorkspaceMembershipStatus.ACTIVE);
            assertThat(m.getJoinedAt()).isEqualTo(JOINED_AT);
        }

        @Test
        @DisplayName("should have null endedAt initially")
        void shouldHaveNullEndedAt() {
            WorkspaceMembership m = WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, USER_ID, JOINED_AT);

            assertThat(m.getEndedAt()).isNull();
        }

        @Test
        @DisplayName("should reject null ID")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> WorkspaceMembership.createOwner(null, WORKSPACE_ID, USER_ID, JOINED_AT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null workspaceId")
        void shouldRejectNullWorkspaceId() {
            assertThatThrownBy(() -> WorkspaceMembership.createOwner(FIXED_ID, null, USER_ID, JOINED_AT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null userId")
        void shouldRejectNullUserId() {
            assertThatThrownBy(() -> WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, null, JOINED_AT))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null joinedAt")
        void shouldRejectNullJoinedAt() {
            assertThatThrownBy(() -> WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, USER_ID, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("should contain key fields")
        void shouldContainKeyFields() {
            WorkspaceMembership m = WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, USER_ID, JOINED_AT);

            String result = m.toString();

            assertThat(result).contains("WorkspaceMembership");
            assertThat(result).contains("OWNER");
            assertThat(result).contains("ACTIVE");
        }
    }

    // ── Equality ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWithSameId() {
            WorkspaceMembership m1 = WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, USER_ID, JOINED_AT);
            UUID otherWorkspace = UUID.fromString("0191f5a0-9999-7abc-8def-012345678900");
            UUID otherUser = UUID.fromString("0191f5a0-8888-7abc-8def-012345678900");
            WorkspaceMembership m2 = WorkspaceMembership.createOwner(FIXED_ID, otherWorkspace, otherUser, JOINED_AT);

            assertThat(m1).isEqualTo(m2);
        }

        @Test
        @DisplayName("should not be equal with different IDs")
        void shouldNotBeEqualWithDifferentId() {
            WorkspaceMembership m1 = WorkspaceMembership.createOwner(FIXED_ID, WORKSPACE_ID, USER_ID, JOINED_AT);
            WorkspaceMembership m2 = WorkspaceMembership.createOwner(
                    UUID.fromString("0191f5a0-5678-7abc-8def-0123456789ab"),
                    WORKSPACE_ID, USER_ID, JOINED_AT);

            assertThat(m1).isNotEqualTo(m2);
        }
    }
}
