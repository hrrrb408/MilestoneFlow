-- V006__remove_redundant_auth_session_family_index.sql
-- Remove the redundant non-unique index on (session_family_id, refresh_generation).
-- The unique constraint uk_auth_session_family_gen already creates an implicit
-- unique B-tree index on the same columns with the same order. The non-unique
-- idx_auth_session_family is therefore a complete duplicate.
--
-- Preserved:
--   uk_auth_session_family_gen  (UNIQUE index from constraint)
--   idx_auth_session_family_status  (different columns: session_family_id, status)
--
-- Not modified: V005 (already applied in shared CI).

DROP INDEX IF EXISTS idx_auth_session_family;
