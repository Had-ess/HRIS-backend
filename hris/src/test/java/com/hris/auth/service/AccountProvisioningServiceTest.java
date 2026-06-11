package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.access.entity.AccessProfile;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.service.UserAccessAssignmentService;
import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.identity.account.LocalAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class AccountProvisioningServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccessProfileRepository accessProfileRepository;
    @Mock private UserAccessAssignmentService userAccessAssignmentService;
    @Mock private LocalAccountService localAccountService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AccountProvisioningService accountProvisioningService;

    private AccountProvisioningRequest request(UUID profileId) {
        return new AccountProvisioningRequest(
            "new.user",
            "new.user@demo.hris.local",
            "New",
            "User",
            List.of(profileId)
        );
    }

    private AccessProfile activeProfile(UUID profileId) {
        return AccessProfile.builder()
            .id(profileId)
            .code("SELF_SERVICE")
            .displayKey("profile.selfService")
            .isActive(true)
            .build();
    }

    @Test
    @DisplayName("rejects provisioning when email already exists")
    void rejectsDuplicateEmail() {
        UUID profileId = UUID.randomUUID();
        when(userRepository.findByEmail("new.user@demo.hris.local"))
            .thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> accountProvisioningService.provision(request(profileId), UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("User email must be unique");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("creates local user, assigns profiles, and initiates activation in one flow")
    void createsUserAssignsProfilesAndInitiatesActivation() {
        UUID profileId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userRepository.findByEmail("new.user@demo.hris.local")).thenReturn(Optional.empty());
        when(accessProfileRepository.findByIdIn(List.of(profileId)))
            .thenReturn(List.of(activeProfile(profileId)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User saved = accountProvisioningService.provision(request(profileId), actorId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new.user@demo.hris.local");
        assertThat(captor.getValue().isSeed()).isTrue();

        verify(userAccessAssignmentService).assignProfile(saved.getId(), profileId, actorId);
        verify(localAccountService).initiateActivation(saved);
    }

    @Test
    @DisplayName("rejects inactive access profiles")
    void rejectsInactiveProfiles() {
        UUID profileId = UUID.randomUUID();
        AccessProfile inactive = AccessProfile.builder()
            .id(profileId)
            .code("SELF_SERVICE")
            .displayKey("profile.selfService")
            .isActive(false)
            .build();

        when(userRepository.findByEmail("new.user@demo.hris.local")).thenReturn(Optional.empty());
        when(accessProfileRepository.findByIdIn(List.of(profileId))).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> accountProvisioningService.provision(request(profileId), UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only active access profiles can be assigned");

        verify(userRepository, never()).save(any(User.class));
        verify(localAccountService, never()).initiateActivation(any());
    }

    @Test
    @DisplayName("requires at least one access profile")
    void requiresAtLeastOneProfile() {
        AccountProvisioningRequest emptyProfiles = new AccountProvisioningRequest(
            "new.user", "new.user@demo.hris.local", "New", "User", List.of());

        assertThatThrownBy(() -> accountProvisioningService.provision(emptyProfiles, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("audits the provisioning with the acting user")
    void auditsProvisioning() {
        UUID profileId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userRepository.findByEmail("new.user@demo.hris.local")).thenReturn(Optional.empty());
        when(accessProfileRepository.findByIdIn(List.of(profileId)))
            .thenReturn(List.of(activeProfile(profileId)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User saved = accountProvisioningService.provision(request(profileId), actorId);

        verify(auditLogService).log(eq(actorId), any(), eq("user"), eq(saved.getId()), any(), any());
    }
}
