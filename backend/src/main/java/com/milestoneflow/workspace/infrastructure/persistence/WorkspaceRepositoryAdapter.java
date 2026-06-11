package com.milestoneflow.workspace.infrastructure.persistence;

import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.domain.exception.WorkspaceSlugAlreadyExistsException;
import com.milestoneflow.workspace.domain.model.Workspace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 *
 * <p>Converts database unique-constraint violations for
 * {@code uk_workspace_slug} into the application-level
 * {@link WorkspaceSlugAlreadyExistsException} at the infrastructure boundary.
 */
@Component
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private static final String UK_WORKSPACE_SLUG = "uk_workspace_slug";

    private final SpringDataWorkspaceRepository delegate;

    WorkspaceRepositoryAdapter(SpringDataWorkspaceRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Workspace save(Workspace workspace) {
        try {
            return delegate.saveAndFlush(workspace);
        } catch (DataIntegrityViolationException ex) {
            if (isSlugConstraintViolation(ex)) {
                throw new WorkspaceSlugAlreadyExistsException();
            }
            throw ex;
        }
    }

    @Override
    public Optional<Workspace> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Workspace> findBySlug(String slug) {
        return delegate.findBySlug(slug);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return delegate.existsBySlug(slug);
    }

    private static boolean isSlugConstraintViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current.getClass().getName()
                    .equals("org.hibernate.exception.ConstraintViolationException")) {
                try {
                    var method = current.getClass().getMethod("getConstraintName");
                    String constraintName = (String) method.invoke(current);
                    if (UK_WORKSPACE_SLUG.equals(constraintName)) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // Reflection failed — not the expected Hibernate type
                }
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
