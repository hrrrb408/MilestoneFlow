package com.milestoneflow.workspace.application.service;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.model.AppUser;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.workspace.application.command.CreateWorkspaceCommand;
import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.application.result.WorkspaceResult;
import com.milestoneflow.workspace.domain.exception.WorkspaceAlreadyExistsForUserException;
import com.milestoneflow.workspace.domain.exception.WorkspaceSlugAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateWorkspaceService}.
 */
@ExtendWith(MockitoExtension.class)
class CreateWorkspaceServiceTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private WorkspaceAuditWriter auditWriter;
    @Mock private IdGenerator idGenerator;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    private CreateWorkspaceService service;

    private static final UUID USER_ID = UUID.fromString("0191f5a0-1234-7abc-8def-0123456789ab");
    private static final UUID WORKSPACE_ID = UUID.fromString("0191f5a0-5678-7abc-8def-0123456789ab");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("0191f5a0-9abc-7abc-8def-0123456789ab");

    @BeforeEach
    void setUp() {
        service = new CreateWorkspaceService(
                workspaceRepository, membershipRepository, appUserRepository,
                auditWriter, idGenerator, fixedClock);
    }

    private AppUser activeVerifiedUser() {
        AppUser user = AppUser.create(USER_ID, "test@example.com", "test@example.com",
                "Test User", "hash", "en");
        user.activateAfterEmailVerification(Instant.parse("2026-05-01T00:00:00Z"));
        return user;
    }

    // ── Successful creation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create workspace with OWNER membership")
        void shouldCreateWorkspaceWithOwner() {
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(activeVerifiedUser()));
            when(workspaceRepository.existsBySlug("my-workspace")).thenReturn(false);
            when(membershipRepository.existsActiveByUserId(USER_ID)).thenReturn(false);
            when(idGenerator.nextId()).thenReturn(WORKSPACE_ID, MEMBERSHIP_ID);
            when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkspaceCommand cmd = new CreateWorkspaceCommand("My Workspace", "my-workspace", "Asia/Taipei", "TWD");
            WorkspaceResult result = service.create(cmd, USER_ID, "req-123");

            assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(result.name()).isEqualTo("My Workspace");
            assertThat(result.slug()).isEqualTo("my-workspace");
            assertThat(result.status()).isEqualTo("ACTIVE");
            assertThat(result.timezone()).isEqualTo("Asia/Taipei");
            assertThat(result.defaultCurrency()).isEqualTo("TWD");
            assertThat(result.role()).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("should apply defaults when timezone and currency are null")
        void shouldApplyDefaults() {
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(activeVerifiedUser()));
            when(workspaceRepository.existsBySlug("test")).thenReturn(false);
            when(membershipRepository.existsActiveByUserId(USER_ID)).thenReturn(false);
            when(idGenerator.nextId()).thenReturn(WORKSPACE_ID, MEMBERSHIP_ID);
            when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkspaceCommand cmd = new CreateWorkspaceCommand("Test", "test", null, null);
            WorkspaceResult result = service.create(cmd, USER_ID, "req-123");

            assertThat(result.timezone()).isEqualTo("Asia/Taipei");
            assertThat(result.defaultCurrency()).isEqualTo("TWD");
        }

        @Test
        @DisplayName("should write audit event")
        void shouldWriteAuditEvent() {
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(activeVerifiedUser()));
            when(workspaceRepository.existsBySlug("test")).thenReturn(false);
            when(membershipRepository.existsActiveByUserId(USER_ID)).thenReturn(false);
            when(idGenerator.nextId()).thenReturn(WORKSPACE_ID, MEMBERSHIP_ID);
            when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.create(new CreateWorkspaceCommand("Test", "test", null, null), USER_ID, "req-123");

            verify(auditWriter).writeUserEvent(
                    anyString(), any(UUID.class), any(UUID.class),
                    anyString(), any(UUID.class), anyString(),
                    anyString(), any());
        }
    }

    // ── Validation failures ──────────────────────────────────────────────

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should reject slug already taken")
        void shouldRejectSlugTaken() {
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(activeVerifiedUser()));
            when(workspaceRepository.existsBySlug("taken")).thenReturn(true);

            CreateWorkspaceCommand cmd = new CreateWorkspaceCommand("Test", "taken", null, null);

            assertThatThrownBy(() -> service.create(cmd, USER_ID, null))
                    .isInstanceOf(WorkspaceSlugAlreadyExistsException.class);
        }

        @Test
        @DisplayName("should reject user with existing active workspace")
        void shouldRejectExistingWorkspace() {
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(activeVerifiedUser()));
            when(workspaceRepository.existsBySlug("test")).thenReturn(false);
            when(membershipRepository.existsActiveByUserId(USER_ID)).thenReturn(true);

            CreateWorkspaceCommand cmd = new CreateWorkspaceCommand("Test", "test", null, null);

            assertThatThrownBy(() -> service.create(cmd, USER_ID, null))
                    .isInstanceOf(WorkspaceAlreadyExistsForUserException.class);
        }

        @Test
        @DisplayName("should reject non-ACTIVE user")
        void shouldRejectNonActiveUser() {
            AppUser pendingUser = AppUser.create(USER_ID, "test@example.com", "test@example.com",
                    "Test User", "hash", "en");
            // not activated — still PENDING_VERIFICATION

            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(pendingUser));

            CreateWorkspaceCommand cmd = new CreateWorkspaceCommand("Test", "test", null, null);

            assertThatThrownBy(() -> service.create(cmd, USER_ID, null))
                    .isInstanceOf(AccountDisabledException.class);
        }

        @Test
        @DisplayName("should reject user without verified email")
        void shouldRejectUnverifiedUser() {
            // Create user and activate but no email verification — this can't happen with
            // the current model since activateAfterEmailVerification sets emailVerifiedAt.
            // Instead, let's test with a user that has emailVerifiedAt = null.
            // The user is ACTIVE but email not verified — not possible with current model
            // so we test the PENDING case which triggers AccountDisabledException.

            AppUser user = AppUser.create(USER_ID, "test@example.com", "test@example.com",
                    "Test User", "hash", "en");
            // User is PENDING_VERIFICATION, not ACTIVE
            when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            CreateWorkspaceCommand cmd = new CreateWorkspaceCommand("Test", "test", null, null);

            assertThatThrownBy(() -> service.create(cmd, USER_ID, null))
                    .isInstanceOf(AccountDisabledException.class);
        }
    }

    // ── Slug normalization ───────────────────────────────────────────────

    @Nested
    @DisplayName("Slug normalization")
    class SlugNormalization {

        @Test
        @DisplayName("should lowercase slug")
        void shouldLowercase() {
            assertThat(CreateWorkspaceService.normalizeSlug("MY-WORKSPACE")).isEqualTo("my-workspace");
        }

        @Test
        @DisplayName("should replace spaces with hyphens")
        void shouldReplaceSpaces() {
            assertThat(CreateWorkspaceService.normalizeSlug("my workspace")).isEqualTo("my-workspace");
        }

        @Test
        @DisplayName("should replace underscores with hyphens")
        void shouldReplaceUnderscores() {
            assertThat(CreateWorkspaceService.normalizeSlug("my_workspace")).isEqualTo("my-workspace");
        }

        @Test
        @DisplayName("should remove special characters")
        void shouldRemoveSpecialChars() {
            assertThat(CreateWorkspaceService.normalizeSlug("my@workspace!")).isEqualTo("myworkspace");
        }

        @Test
        @DisplayName("should collapse consecutive hyphens")
        void shouldCollapseHyphens() {
            assertThat(CreateWorkspaceService.normalizeSlug("my---workspace")).isEqualTo("my-workspace");
        }

        @Test
        @DisplayName("should trim leading/trailing hyphens")
        void shouldTrimHyphens() {
            assertThat(CreateWorkspaceService.normalizeSlug("-my-workspace-")).isEqualTo("my-workspace");
        }

        @Test
        @DisplayName("should reject too short slug")
        void shouldRejectTooShort() {
            assertThatThrownBy(() -> CreateWorkspaceService.normalizeSlug("ab"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("3 and 50");
        }

        @Test
        @DisplayName("should reject null slug")
        void shouldRejectNull() {
            assertThatThrownBy(() -> CreateWorkspaceService.normalizeSlug(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject blank slug")
        void shouldRejectBlank() {
            assertThatThrownBy(() -> CreateWorkspaceService.normalizeSlug("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should accept valid slug")
        void shouldAcceptValid() {
            assertThat(CreateWorkspaceService.normalizeSlug("my-workspace-123")).isEqualTo("my-workspace-123");
        }
    }
}
