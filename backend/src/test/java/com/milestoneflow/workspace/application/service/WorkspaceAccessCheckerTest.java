package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceNotFoundException;
import com.milestoneflow.workspace.domain.model.Workspace;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkspaceAccessChecker}.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceAccessCheckerTest {

    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private WorkspaceRepository workspaceRepository;

    private WorkspaceAccessChecker accessChecker;

    private static final UUID WORKSPACE_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");
    private static final UUID USER_ID = UUID.fromString("0191f5a0-5678-7abc-8def-0123456789ab");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("0191f5a0-9abc-7abc-8def-0123456789ab");

    @BeforeEach
    void setUp() {
        accessChecker = new WorkspaceAccessChecker(membershipRepository, workspaceRepository);
    }

    private WorkspaceMembership activeOwnerMembership() {
        return WorkspaceMembership.createOwner(MEMBERSHIP_ID, WORKSPACE_ID, USER_ID,
                Instant.parse("2026-06-01T12:00:00Z"));
    }

    private Workspace activeWorkspace() {
        return Workspace.create(WORKSPACE_ID, "Test", "test", "TWD", "Asia/Taipei");
    }

    // ── requireActiveMember ──────────────────────────────────────────────

    @Nested
    @DisplayName("requireActiveMember")
    class RequireActiveMember {

        @Test
        @DisplayName("should return membership when active member exists")
        void shouldReturnMembershipWhenActive() {
            WorkspaceMembership membership = activeOwnerMembership();
            when(membershipRepository.findActiveByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                    .thenReturn(Optional.of(membership));

            WorkspaceMembership result = accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID);

            assertThat(result).isEqualTo(membership);
        }

        @Test
        @DisplayName("should throw when no active membership exists")
        void shouldThrowWhenNoMembership() {
            when(membershipRepository.findActiveByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }
    }

    // ── requireOwner ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireOwner")
    class RequireOwner {

        @Test
        @DisplayName("should return membership when user is OWNER")
        void shouldReturnWhenOwner() {
            WorkspaceMembership membership = activeOwnerMembership();
            when(membershipRepository.findActiveByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                    .thenReturn(Optional.of(membership));

            WorkspaceMembership result = accessChecker.requireOwner(WORKSPACE_ID, USER_ID);

            assertThat(result.getRole()).isEqualTo(WorkspaceMembershipRole.OWNER);
        }

        @Test
        @DisplayName("should throw when no membership exists")
        void shouldThrowWhenNoMembership() {
            when(membershipRepository.findActiveByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accessChecker.requireOwner(WORKSPACE_ID, USER_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
        }
    }

    // ── findWorkspaceOrThrow ─────────────────────────────────────────────

    @Nested
    @DisplayName("findWorkspaceOrThrow")
    class FindWorkspaceOrThrow {

        @Test
        @DisplayName("should return workspace when found")
        void shouldReturnWhenFound() {
            Workspace workspace = activeWorkspace();
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

            Workspace result = accessChecker.findWorkspaceOrThrow(WORKSPACE_ID);

            assertThat(result.getId()).isEqualTo(WORKSPACE_ID);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accessChecker.findWorkspaceOrThrow(WORKSPACE_ID))
                    .isInstanceOf(WorkspaceNotFoundException.class);
        }
    }
}
