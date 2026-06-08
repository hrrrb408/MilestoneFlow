package com.milestoneflow.identity.application.service;

import com.milestoneflow.identity.application.port.out.AppUserRepository;
import com.milestoneflow.identity.application.result.CurrentUserResult;
import com.milestoneflow.identity.domain.exception.AccountDisabledException;
import com.milestoneflow.identity.domain.model.AppUser;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCurrentUserService")
class GetCurrentUserServiceTest {

    @Mock private AppUserRepository userRepository;

    private GetCurrentUserService service;

    private static final UUID USER_ID = UUID.fromString("01923456-7890-7abc-def0-123456789abc");

    @BeforeEach
    void setUp() {
        service = new GetCurrentUserService(userRepository);
    }

    private AppUser createActiveUser() {
        var user = AppUser.create(USER_ID, "user@example.com", "user@example.com",
                "Test User", "{bcrypt}$2a$10$hash", "en");
        user.activateAfterEmailVerification(Instant.now());
        return user;
    }

    @Nested
    @DisplayName("successful fetch")
    class SuccessfulFetch {

        @Test
        @DisplayName("returns correct user data")
        void returnsCorrectData() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createActiveUser()));

            CurrentUserResult result = service.getCurrentUser(USER_ID);

            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.email()).isEqualTo("user@example.com");
            assertThat(result.displayName()).isEqualTo("Test User");
            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("does not return sensitive fields")
        void doesNotReturnSensitiveFields() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createActiveUser()));

            CurrentUserResult result = service.getCurrentUser(USER_ID);

            var str = result.toString();
            assertThat(str).doesNotContain("password");
            assertThat(str).doesNotContain("hash");
            assertThat(str).doesNotContain("token");
        }
    }

    @Nested
    @DisplayName("failure cases")
    class FailureCases {

        @Test
        @DisplayName("user not found throws exception")
        void userNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCurrentUser(USER_ID))
                    .isInstanceOf(AccountDisabledException.class);
        }

        @Test
        @DisplayName("disabled user throws exception")
        void disabledUser() {
            var user = createActiveUser();
            user.disable();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.getCurrentUser(USER_ID))
                    .isInstanceOf(AccountDisabledException.class);
        }
    }
}
