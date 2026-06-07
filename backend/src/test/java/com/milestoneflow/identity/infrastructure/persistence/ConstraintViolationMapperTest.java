package com.milestoneflow.identity.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConstraintViolationMapper")
class ConstraintViolationMapperTest {

    @Nested
    @DisplayName("isDuplicateEmail")
    class IsDuplicateEmail {

        @Test
        @DisplayName("returns true for uk_app_user_email_normalized constraint")
        void emailConstraint() {
            SQLException sqlEx = new SQLException("duplicate key value", "23505");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx,
                    ConstraintViolationMapper.UK_APP_USER_EMAIL_NORMALIZED);
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isTrue();
        }

        @Test
        @DisplayName("returns false for other unique constraint")
        void otherUniqueConstraint() {
            SQLException sqlEx = new SQLException("duplicate key value", "23505");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx,
                    "uk_verification_token_hash");
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isFalse();
        }

        @Test
        @DisplayName("returns false for NOT NULL violation without constraint name")
        void notNullViolation() {
            SQLException sqlEx = new SQLException("null value in column", "23502");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx, null);
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isFalse();
        }

        @Test
        @DisplayName("returns false for CHECK violation")
        void checkViolation() {
            SQLException sqlEx = new SQLException("check constraint", "23514");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx,
                    "ck_app_user_status");
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isFalse();
        }

        @Test
        @DisplayName("returns false for FK violation")
        void fkViolation() {
            SQLException sqlEx = new SQLException("foreign key violation", "23503");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx,
                    "fk_verification_token_user");
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isFalse();
        }

        @Test
        @DisplayName("returns false when cause chain has no ConstraintViolationException")
        void noConstraintViolationException() {
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "some other error", new RuntimeException("root cause"));

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isFalse();
        }

        @Test
        @DisplayName("returns false for deeply nested email constraint")
        void deeplyNestedEmailConstraint() {
            SQLException sqlEx = new SQLException("duplicate key value", "23505");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx,
                    ConstraintViolationMapper.UK_APP_USER_EMAIL_NORMALIZED);
            // Wrap in multiple layers
            RuntimeException middle = new RuntimeException("wrapper", hibernateEx);
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", middle);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isTrue();
        }

        @Test
        @DisplayName("returns false when constraint name is blank")
        void blankConstraintName() {
            SQLException sqlEx = new SQLException("duplicate key value", "23505");
            ConstraintViolationException hibernateEx = new ConstraintViolationException(
                    "could not execute statement", sqlEx, "   ");
            DataIntegrityViolationException dive = new DataIntegrityViolationException(
                    "could not execute statement", hibernateEx);

            assertThat(ConstraintViolationMapper.isDuplicateEmail(dive)).isFalse();
        }
    }
}
