package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.result.WorkspaceMemberResult;
import com.milestoneflow.workspace.application.result.WorkspaceMembersResult;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListWorkspaceMembersService}.
 */
@ExtendWith(MockitoExtension.class)
class ListWorkspaceMembersServiceTest {

    @Mock private WorkspaceAccessChecker accessChecker;
    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private WorkspaceAuditWriter auditWriter;

    private ListWorkspaceMembersService service;

    private static final UUID WORKSPACE_ID = UUID.fromString("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");
    private static final UUID USER_ID = UUID.fromString("0192a3b4-c5d6-7e8f-9a0b-111111111111");
    private static final String REQUEST_ID = "req-123";

    @BeforeEach
    void setUp() {
        service = new ListWorkspaceMembersService(accessChecker, membershipRepository, auditWriter);
    }

    private WorkspaceMemberResult ownerMember() {
        return new WorkspaceMemberResult(USER_ID, "owner@example.com", "Owner",
                "OWNER", "ACTIVE", Instant.parse("2026-06-01T12:00:00Z"));
    }

    // ── Active member queries ────────────────────────────────────────────

    @Nested
    @DisplayName("listMembers (active member)")
    class ActiveMember {

        @Test
        @DisplayName("should return ACTIVE members when caller is a member")
        void shouldReturnMembers() {
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(WorkspaceMembership.createOwner(UUID.randomUUID(), WORKSPACE_ID, USER_ID,
                            Instant.parse("2026-06-01T12:00:00Z")));
            when(membershipRepository.findActiveMembersByWorkspaceId(WORKSPACE_ID))
                    .thenReturn(List.of(ownerMember()));

            WorkspaceMembersResult result = service.listMembers(WORKSPACE_ID, USER_ID, REQUEST_ID);

            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.members()).hasSize(1);
            WorkspaceMemberResult member = result.members().get(0);
            assertThat(member.userId()).isEqualTo(USER_ID);
            assertThat(member.email()).isEqualTo("owner@example.com");
            assertThat(member.role()).isEqualTo("OWNER");
            assertThat(member.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should return empty list when workspace has no ACTIVE members")
        void shouldReturnEmptyWhenNoMembers() {
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(WorkspaceMembership.createOwner(UUID.randomUUID(), WORKSPACE_ID, USER_ID,
                            Instant.parse("2026-06-01T12:00:00Z")));
            when(membershipRepository.findActiveMembersByWorkspaceId(WORKSPACE_ID))
                    .thenReturn(List.of());

            WorkspaceMembersResult result = service.listMembers(WORKSPACE_ID, USER_ID, REQUEST_ID);

            assertThat(result.members()).isEmpty();
        }

        @Test
        @DisplayName("should preserve repository ordering (joinedAt ascending)")
        void shouldPreserveOrdering() {
            WorkspaceMemberResult first = new WorkspaceMemberResult(
                    UUID.randomUUID(), "a@example.com", "A", "OWNER", "ACTIVE",
                    Instant.parse("2026-06-01T00:00:00Z"));
            WorkspaceMemberResult second = new WorkspaceMemberResult(
                    UUID.randomUUID(), "b@example.com", "B", "OWNER", "ACTIVE",
                    Instant.parse("2026-06-02T00:00:00Z"));

            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(WorkspaceMembership.createOwner(UUID.randomUUID(), WORKSPACE_ID, USER_ID,
                            Instant.parse("2026-06-01T12:00:00Z")));
            when(membershipRepository.findActiveMembersByWorkspaceId(WORKSPACE_ID))
                    .thenReturn(List.of(first, second));

            WorkspaceMembersResult result = service.listMembers(WORKSPACE_ID, USER_ID, REQUEST_ID);

            assertThat(result.members()).containsExactly(first, second);
        }

        @Test
        @DisplayName("should write WORKSPACE_MEMBERS_VIEWED audit with memberCount metadata")
        @SuppressWarnings("unchecked")
        void shouldAuditWithMemberCount() {
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenReturn(WorkspaceMembership.createOwner(UUID.randomUUID(), WORKSPACE_ID, USER_ID,
                            Instant.parse("2026-06-01T12:00:00Z")));
            when(membershipRepository.findActiveMembersByWorkspaceId(WORKSPACE_ID))
                    .thenReturn(List.of(ownerMember()));

            service.listMembers(WORKSPACE_ID, USER_ID, REQUEST_ID);

            ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
            verify(auditWriter).writeUserEvent(
                    org.mockito.ArgumentMatchers.eq("WORKSPACE_MEMBERS_VIEWED"),
                    org.mockito.ArgumentMatchers.eq(USER_ID),
                    org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                    org.mockito.ArgumentMatchers.eq("workspace_membership"),
                    org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq(REQUEST_ID),
                    org.mockito.ArgumentMatchers.anyString(),
                    metaCaptor.capture());

            assertThat(metaCaptor.getValue()).containsEntry("memberCount", 1);
        }
    }

    // ── Non-member / denied ──────────────────────────────────────────────

    @Nested
    @DisplayName("listMembers (non-member)")
    class NonMember {

        @Test
        @DisplayName("should propagate WorkspaceAccessDeniedException for non-member")
        void shouldThrowForNonMember() {
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.listMembers(WORKSPACE_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);

            // Projection must never run, and no audit on denied access.
            verify(membershipRepository, never()).findActiveMembersByWorkspaceId(any());
            verify(auditWriter, never()).writeUserEvent(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
