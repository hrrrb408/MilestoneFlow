# ADR Decision Matrix

| ADR | Topic | Previous Status | Decision | Needs Changes | Blocks B1 | Change Owner | Re-review Required |
|-----|-------|-----------------|----------|---------------|-----------|--------------|-------------------|
| BE-001 | Package & module structure | Proposed | **Accepted with changes** | Yes: add module list, shared rules, ArchUnit minimum | No (after changes) | Architect | No |
| BE-002 | UUID generation | Proposed | **Accepted** | No. Library deferred. | No | Developer | No |
| BE-003 | Money mapping | Proposed | **Accepted** | No | No (not in B1) | Developer | No |
| BE-004 | JSONB mapping | Proposed | **Accepted** | No | No | Developer | No |
| BE-005 | Auditing fields | Proposed | **Accepted with changes** | Yes: clarify JPA auditing ≠ audit_event, system user, @MappedSuperclass | No (after changes) | Architect | No |
| BE-006 | Composite FK & tenant isolation | Proposed | **Accepted** | No | No | Developer | No |
| BE-007 | Projection query | Proposed | **Accepted** | No | No (not in B1) | Developer | No |
| BE-008 | Flyway & JPA startup | Proposed | **Accepted** | No | No | Developer | No |
| BE-009 | API response envelope | Proposed | **Accepted with changes** | Yes: add pagination meta, 204 rules | No (after changes) | Architect | No |
| BE-010 | HTTP validation status | Proposed | **Accepted** | No | No | Developer | No |

## Summary Statistics

- **Accepted**: 7 (BE-002, BE-003, BE-004, BE-006, BE-007, BE-008, BE-010)
- **Accepted with changes**: 3 (BE-001, BE-005, BE-009)
- **Rejected**: 0
- **Deferred**: 0
- **Blocks B1**: 0 (after required changes are applied)
