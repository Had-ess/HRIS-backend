package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.access.entity.AccessProfile;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.service.UserAccessAssignmentService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.KeycloakProvisioningException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccessProfileRepository accessProfileRepository;
    @Mock private UserAccessAssignmentService userAccessAssignmentService;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AccountProvisioningService accountProvisioningService;

    @Test
    @DisplayName("propagates Keycloak provisioning conflict without wrapping it")
    void propagatesKeycloakProvisioningConflictWithoutWrappingIt() {
        UUID profileId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AccountProvisioningRequest request = new AccountProvisioningRequest(
            "new.user",
            "new.user@demo.hris.local",
            "New",
            "User",
            "Temp123!",
            false,
            List.of(profileId)
        );
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("SELF_SERVICE")
            .displayKey("profile.selfService")
            .isActive(true)
            .build();

        KeycloakProvisioningException exception = new KeycloakProvisioningException(
            HttpStatus.CONFLICT,
            "A Keycloak user with this username already exists",
            "create user",
            HttpStatus.CONFLICT,
            "{\"errorMessage\":\"User exists with same username\"}",
            null
        );

        when(userRepository.findByEmail("new.user@demo.hris.local")).thenReturn(Optional.empty());
        when(accessProfileRepository.findByIdIn(List.of(profileId))).thenReturn(List.of(profile));
        when(keycloakAdminClient.createUser(any())).thenThrow(exception);

        assertThatThrownBy(() -> accountProvisioningService.provision(request, actorId))
            .isSameAs(exception)
            .hasMessage("A Keycloak user with this username already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("deletes created Keycloak user when local user save fails")
    void deletesCreatedKeycloakUserWhenLocalUserSaveFails() {
        UUID profileId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AccountProvisioningRequest request = new AccountProvisioningRequest(
            "new.user",
            "new.user@demo.hris.local",
            "New",
            "User",
            "Temp123!",
            false,
            List.of(profileId)
        );
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("SELF_SERVICE")
            .displayKey("profile.selfService")
            .isActive(true)
            .build();

        when(userRepository.findByEmail("new.user@demo.hris.local")).thenReturn(Optional.empty());
        when(accessProfileRepository.findByIdIn(List.of(profileId))).thenReturn(List.of(profile));
        when(keycloakAdminClient.createUser(any())).thenReturn("kc-user-123");
        when(userRepository.save(any(User.class))).thenThrow(new IllegalStateException("Local save failed"));

        assertThatThrownBy(() -> accountProvisioningService.provision(request, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Local save failed");

        verify(keycloakAdminClient).deleteUser("kc-user-123");
    }
}
