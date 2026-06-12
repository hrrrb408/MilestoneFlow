package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.result.CurrentWorkspaceMembershipResult;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetCurrentWorkspaceMembershipService}.
 */
@ExtendWith(MockitoExtension.class)
class GetCurrentWorkspaceMembershipServiceTest {

    @Mock private WorkspaceAccessChecker accessChecker;
    @Mock private WorkspaceAuditWriter auditWriter;

    private GetCurrentWorkspaceMembershipService service;

    private static final UUID WORKSPACE_ID = UUID.fromString("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");
    private static final UUID USER_ID = UUID.fromString("0192a3b4-c5d6-7e8f-9a0b-111111111111");
    private static final String REQUEST_ID = "req-456";
    private static final Instant JOINED_AT = Instant.parse("2026-06-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new GetCurrentWorkspaceMembershipService(accessChecker, auditWriter);
    }

    @Nested
    @DisplayName("getCurrentMembership (active member)")
    class ActiveMember {

        @Test
        @DisplayName("should return current user's membership when active")
        void shouldReturnMembership() {
            WorkspaceMembership membership = WorkspaceMembership.createOwner(
                    UUID.randomUUID(), WORKSPACE_ID, USER_ID, JOINED_AT);
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID)).thenReturn(membership);

            CurrentWorkspaceMembershipResult result = service.getCurrentMembership(WORKSPACE_ID, USER_ID, REQUEST_ID);

            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.role()).isEqualTo("OWNER");
            assertThat(result.status()).isEqualTo("ACTIVE");
            assertThat(result.joinedAt()).isEqualTo(JOINED_AT);
        }

        @Test
        @DisplayName("should write WORKSPACE_MEMBER_SELF_VIEWED audit with no metadata")
        void shouldAuditSelfView() {
            WorkspaceMembership membership = WorkspaceMembership.createOwner(
                    UUID.randomUUID(), WORKSPACE_ID, USER_ID, JOINED_AT);
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID)).thenReturn(membership);

            service.getCurrentMembership(WORKSPACE_ID, USER_ID, REQUEST_ID);

            verify(auditWriter).writeUserEvent(
                    org.mockito.ArgumentMatchers.eq("WORKSPACE_MEMBER_SELF_VIEWED"),
                    org.mockito.ArgumentMatchers.eq(USER_ID),
                    org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                    org.mockito.ArgumentMatchers.eq("workspace_membership"),
                    org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq(REQUEST_ID),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.isNull());
        }
    }

    @Nested
    @DisplayName("getCurrentMembership (non-member)")
    class NonMember {

        @Test
        @DisplayName("should propagate WorkspaceAccessDeniedException for non-member")
        void shouldThrowForNonMember() {
            when(accessChecker.requireActiveMember(WORKSPACE_ID, USER_ID))
                    .thenThrow(new WorkspaceAccessDeniedException());

            assertThatThrownBy(() -> service.getCurrentMembership(WORKSPACE_ID, USER_ID, REQUEST_ID))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);

            verify(auditWriter, never()).writeUserEvent(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
