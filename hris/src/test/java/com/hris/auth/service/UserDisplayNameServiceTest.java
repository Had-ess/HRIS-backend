package com.hris.auth.service;

import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDisplayNameService Unit Tests")
class UserDisplayNameServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDisplayNameService userDisplayNameService;

    @Test
    @DisplayName("toDisplayName prefers trimmed full name")
    void toDisplayNamePrefersTrimmedFullName() {
        User user = User.builder()
            .firstName("  Jane ")
            .lastName(" Doe  ")
            .email("jane@example.com")
            .build();

        assertThat(userDisplayNameService.toDisplayName(user)).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("toDisplayName falls back to email when full name is blank")
    void toDisplayNameFallsBackToEmailWhenFullNameIsBlank() {
        User user = User.builder()
            .firstName("   ")
            .lastName(null)
            .email("  fallback@example.com ")
            .build();

        assertThat(userDisplayNameService.toDisplayName(user)).isEqualTo("fallback@example.com");
    }

    @Test
    @DisplayName("resolveDisplayName returns null when user is missing")
    void resolveDisplayNameReturnsNullWhenUserIsMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(userDisplayNameService.resolveDisplayName(userId)).isNull();
    }

    @Test
    @DisplayName("resolveDisplayNames batches users and skips null ids")
    void resolveDisplayNamesBatchesUsersAndSkipsNullIds() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        User firstUser = User.builder()
            .id(firstUserId)
            .firstName("John")
            .lastName("Smith")
            .email("john@example.com")
            .build();
        User secondUser = User.builder()
            .id(secondUserId)
            .firstName(" ")
            .lastName(" ")
            .email("julia@example.com")
            .build();

        when(userRepository.findAllById(List.of(firstUserId, secondUserId)))
            .thenReturn(List.of(firstUser, secondUser));

        List<UUID> requestedIds = new ArrayList<>();
        requestedIds.add(firstUserId);
        requestedIds.add(null);
        requestedIds.add(firstUserId);
        requestedIds.add(secondUserId);

        Map<UUID, String> result = userDisplayNameService.resolveDisplayNames(
            requestedIds
        );

        assertThat(result).containsEntry(firstUserId, "John Smith");
        assertThat(result).containsEntry(secondUserId, "julia@example.com");
        assertThat(result).hasSize(2);
    }
}
