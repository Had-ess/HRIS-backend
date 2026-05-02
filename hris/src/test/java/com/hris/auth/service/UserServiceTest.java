package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.UpdateCurrentUserDto;
import com.hris.auth.dto.UpdateLocaleDto;
import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleAssignmentService userRoleAssignmentService;
    @Mock private AuditLogService auditLogService;
    @Mock private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("updates current user profile and propagates it to Keycloak")
    void updatesCurrentUserProfileAndPropagatesItToKeycloak() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .keycloakId("kc-user-123")
            .email("old@demo.hris.local")
            .firstName("Old")
            .lastName("Name")
            .localePreference("fr")
            .isActive(true)
            .createdAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("new@demo.hris.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRoleAssignmentService.getRoles(userId)).thenReturn(List.of());

        UserResponseDto response = userService.updateCurrentUser(userId, new UpdateCurrentUserDto(
            "new@demo.hris.local",
            "New",
            "Person"
        ));

        assertThat(response.email()).isEqualTo("new@demo.hris.local");
        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Person");
        verify(keycloakAdminClient).updateUserProfile("kc-user-123", "new@demo.hris.local", "New", "Person");
    }

    @Test
    @DisplayName("rejects current user profile update when email belongs to another user")
    void rejectsCurrentUserProfileUpdateWhenEmailBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .keycloakId("kc-user-123")
            .email("old@demo.hris.local")
            .firstName("Old")
            .lastName("Name")
            .localePreference("fr")
            .isActive(true)
            .createdAt(Instant.now())
            .build();
        User otherUser = User.builder()
            .id(UUID.randomUUID())
            .keycloakId("kc-user-999")
            .email("taken@demo.hris.local")
            .firstName("Taken")
            .lastName("User")
            .localePreference("fr")
            .isActive(true)
            .createdAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("taken@demo.hris.local")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> userService.updateCurrentUser(userId, new UpdateCurrentUserDto(
            "taken@demo.hris.local",
            "New",
            "Person"
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("User email must be unique");

        verify(keycloakAdminClient, never()).updateUserProfile(any(), any(), any(), any());
    }

    @Test
    @DisplayName("updates locale preference")
    void updatesLocalePreference() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .keycloakId("kc-user-123")
            .email("user@demo.hris.local")
            .firstName("Demo")
            .lastName("User")
            .localePreference("fr")
            .isActive(true)
            .createdAt(Instant.now())
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRoleAssignmentService.getRoles(userId)).thenReturn(List.of());

        UserResponseDto response = userService.updateLocale(userId, new UpdateLocaleDto("en"));

        assertThat(response.localePreference()).isEqualTo("en");
        verify(auditLogService, never()).log(eq(userId), any(), eq("user"), eq(userId), any(), any());
    }
}
