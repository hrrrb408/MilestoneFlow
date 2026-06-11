package com.milestoneflow.workspace.application.service;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.exception.EmailNotVerifiedException;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.workspace.application.command.CreateWorkspaceCommand;
import com.milestoneflow.workspace.application.port.in.CreateWorkspaceUseCase;
import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.application.result.WorkspaceResult;
import com.milestoneflow.workspace.domain.exception.WorkspaceAlreadyExistsForUserException;
import com.milestoneflow.workspace.domain.exception.WorkspaceSlugAlreadyExistsException;
import com.milestoneflow.workspace.domain.model.Workspace;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import com.milestoneflow.identity.domain.type.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Application service for creating a new workspace.
 *
 * <p>Creation flow:
 * <ol>
 *   <li>Load AppUser and confirm ACTIVE status with verified email</li>
 *   <li>Validate and normalize slug</li>
 *   <li>Check slug uniqueness</li>
 *   <li>Check user does not already have an active workspace</li>
 *   <li>Create Workspace in ACTIVE status</li>
 *   <li>Create WorkspaceMembership with OWNER + ACTIVE status</li>
 *   <li>Save both in a single transaction</li>
 *   <li>Write audit event WORKSPACE_CREATED</li>
 * </ol>
 */
@Service
public class CreateWorkspaceService implements CreateWorkspaceUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateWorkspaceService.class);
    private static final String DEFAULT_TIMEZONE = "Asia/Taipei";
    private static final String DEFAULT_CURRENCY = "TWD";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final WorkspaceAuditWriter auditWriter;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public CreateWorkspaceService(WorkspaceRepository workspaceRepository,
                                  WorkspaceMembershipRepository membershipRepository,
                                  AppUserRepository appUserRepository,
                                  WorkspaceAuditWriter auditWriter,
                                  IdGenerator idGenerator,
                                  Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.auditWriter = auditWriter;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkspaceResult create(CreateWorkspaceCommand command, UUID userId, String requestId) {
        // 1. Load user and verify eligibility
        var user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountDisabledException();
        }

        if (user.getEmailVerifiedAt() == null) {
            throw new EmailNotVerifiedException();
        }

        // 2. Normalize slug
        String normalizedSlug = normalizeSlug(command.slug());

        // 3. Check slug uniqueness
        if (workspaceRepository.existsBySlug(normalizedSlug)) {
            throw new WorkspaceSlugAlreadyExistsException();
        }

        // 4. Check user does not already have an active workspace
        if (membershipRepository.existsActiveByUserId(userId)) {
            throw new WorkspaceAlreadyExistsForUserException();
        }

        // 5. Apply defaults
        String timezone = command.timezone() != null ? command.timezone() : DEFAULT_TIMEZONE;
        String currency = command.defaultCurrency() != null ? command.defaultCurrency() : DEFAULT_CURRENCY;

        // 6. Create workspace
        UUID workspaceId = idGenerator.nextId();
        Workspace workspace = Workspace.create(
                workspaceId,
                command.name(),
                normalizedSlug,
                currency,
                timezone
        );

        // 7. Create OWNER membership
        Instant now = Instant.now(clock);
        WorkspaceMembership membership = WorkspaceMembership.createOwner(
                idGenerator.nextId(),
                workspaceId,
                userId,
                now
        );

        // 8. Save both in transaction
        workspaceRepository.save(workspace);
        membershipRepository.save(membership);

        log.info("Workspace created: workspaceId={}, slug={}, ownerId={}", workspaceId, normalizedSlug, userId);

        // 9. Audit
        auditWriter.writeUserEvent("WORKSPACE_CREATED", userId, workspaceId,
                "workspace", workspaceId, requestId,
                "Workspace created", null);

        return new WorkspaceResult(
                workspaceId,
                workspace.getName(),
                workspace.getSlug(),
                workspace.getStatus().name(),
                workspace.getTimezone(),
                workspace.getDefaultCurrency(),
                membership.getRole().name(),
                workspace.getCreatedAt()
        );
    }

    /**
     * Normalizes a slug: lowercase, trim, replace spaces with hyphens,
     * remove non-alphanumeric characters except hyphens.
     *
     * @param slug the raw slug input
     * @return the normalized slug
     */
    static String normalizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Slug must not be empty");
        }

        String normalized = slug.trim().toLowerCase();
        // Replace spaces and underscores with hyphens
        normalized = normalized.replaceAll("[\\s_]+", "-");
        // Remove any character that is not a lowercase letter, digit, or hyphen
        normalized = normalized.replaceAll("[^a-z0-9-]", "");
        // Remove leading/trailing hyphens
        normalized = normalized.replaceAll("^-+|-+$", "");
        // Collapse consecutive hyphens
        normalized = normalized.replaceAll("-{2,}", "-");

        if (normalized.length() < 3 || normalized.length() > 50) {
            throw new IllegalArgumentException("Slug must be between 3 and 50 characters");
        }

        return normalized;
    }
}
